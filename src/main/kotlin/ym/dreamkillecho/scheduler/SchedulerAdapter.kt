package ym.dreamkillecho.scheduler

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

interface SchedulerAdapter {
    val platformName: String
    /**
     * Global/main-safe execution. On Folia this is the global region scheduler,
     * so entity or location specific Bukkit operations must still use runEntity
     * or runLocation.
     */
    fun runMain(task: () -> Unit)
    fun runAsync(task: () -> Unit)
    fun runLater(delayTicks: Long, task: () -> Unit)
    fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit)
    fun runEntity(entity: Entity, task: () -> Unit)
    fun runLocation(location: Location, task: () -> Unit)
    fun cancelAll()

    companion object {
        fun create(plugin: Plugin): SchedulerAdapter {
            return if (FoliaReflectiveSchedulerAdapter.isFoliaAvailable()) {
                FoliaReflectiveSchedulerAdapter(plugin)
            } else {
                BukkitSchedulerAdapter(plugin)
            }
        }
    }
}

private class BukkitSchedulerAdapter(private val plugin: Plugin) : SchedulerAdapter {
    private val tasks = Collections.synchronizedList(mutableListOf<Int>())
    override val platformName: String = "BukkitScheduler"

    override fun runMain(task: () -> Unit) {
        if (Bukkit.isPrimaryThread()) task() else {
            tasks += Bukkit.getScheduler().runTask(plugin, Runnable { task.safeRun(plugin) }).taskId
        }
    }

    override fun runAsync(task: () -> Unit) {
        tasks += Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { task.safeRun(plugin) }).taskId
    }

    override fun runLater(delayTicks: Long, task: () -> Unit) {
        tasks += Bukkit.getScheduler().runTaskLater(plugin, Runnable { task.safeRun(plugin) }, delayTicks).taskId
    }

    override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit) {
        tasks += Bukkit.getScheduler().runTaskTimer(plugin, Runnable { task.safeRun(plugin) }, delayTicks, periodTicks).taskId
    }

    override fun runEntity(entity: Entity, task: () -> Unit) = runMain(task)

    override fun runLocation(location: Location, task: () -> Unit) = runMain(task)

    override fun cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin)
        tasks.clear()
    }
}

private class FoliaReflectiveSchedulerAdapter(private val plugin: Plugin) : SchedulerAdapter {
    override val platformName: String = "FoliaReflectiveScheduler"
    private val scheduledTasks = Collections.synchronizedList(mutableListOf<Any>())

    override fun runMain(task: () -> Unit) {
        invokeGlobal("run", task)
    }

    override fun runAsync(task: () -> Unit) {
        val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
        val method = scheduler.javaClass.methods.first { method ->
            method.name == "runNow" && method.parameterTypes.size == 2
        }
        val scheduled = method.invoke(scheduler, plugin, Consumer<Any> { task.safeRun(plugin) })
        if (scheduled != null) scheduledTasks += scheduled
    }

    override fun runLater(delayTicks: Long, task: () -> Unit) {
        invokeGlobal("runDelayed", task, delayTicks)
    }

    override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit) {
        val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
        val method = scheduler.javaClass.methods.first { method ->
            method.name == "runAtFixedRate" && method.parameterTypes.size == 5
        }
        val scheduled = method.invoke(
            scheduler,
            plugin,
            Consumer<Any> { task.safeRun(plugin) },
            ticksToMillis(delayTicks),
            ticksToMillis(periodTicks),
            TimeUnit.MILLISECONDS
        )
        if (scheduled != null) scheduledTasks += scheduled
    }

    override fun runEntity(entity: Entity, task: () -> Unit) {
        val entityScheduler = entity.javaClass.getMethod("getScheduler").invoke(entity)
        val run = entityScheduler.javaClass.methods.first { method ->
            method.name == "run" && method.parameterTypes.size == 3
        }
        val scheduled = run.invoke(entityScheduler, plugin, Consumer<Any> { task.safeRun(plugin) }, Runnable {})
        if (scheduled != null) scheduledTasks += scheduled
    }

    override fun runLocation(location: Location, task: () -> Unit) {
        val scheduler = Bukkit::class.java.getMethod("getRegionScheduler").invoke(null)
        val method = scheduler.javaClass.methods.first { method ->
            method.name == "run" && method.parameterTypes.size == 5
        }
        val scheduled = method.invoke(
            scheduler,
            plugin,
            location.world,
            location.blockX shr 4,
            location.blockZ shr 4,
            Consumer<Any> { task.safeRun(plugin) }
        )
        if (scheduled != null) scheduledTasks += scheduled
    }

    override fun cancelAll() {
        val copy = scheduledTasks.toList()
        scheduledTasks.clear()
        for (scheduled in copy) {
            runCatching { scheduled.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterCount == 0 }?.invoke(scheduled) }
        }
    }

    private fun invokeGlobal(methodName: String, task: () -> Unit, delayTicks: Long? = null) {
        val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
        val candidates = scheduler.javaClass.methods.filter { it.name == methodName }
        val method = if (delayTicks == null) {
            candidates.first { it.parameterTypes.size == 2 }
        } else {
            candidates.first { it.parameterTypes.size == 3 }
        }
        val scheduled = if (delayTicks == null) {
            method.invoke(scheduler, plugin, Consumer<Any> { task.safeRun(plugin) })
        } else {
            method.invoke(scheduler, plugin, Consumer<Any> { task.safeRun(plugin) }, delayTicks)
        }
        if (scheduled != null) scheduledTasks += scheduled
    }

    private fun ticksToMillis(ticks: Long): Long = (ticks.coerceAtLeast(1L) * 50L)

    companion object {
        fun isFoliaAvailable(): Boolean {
            return runCatching {
                Bukkit::class.java.getMethod("getRegionScheduler")
                Bukkit::class.java.getMethod("getAsyncScheduler")
                Bukkit::class.java.getMethod("getGlobalRegionScheduler")
                true
            }.getOrDefault(false)
        }
    }
}

private fun (() -> Unit).safeRun(plugin: Plugin) {
    try {
        invoke()
    } catch (ex: Exception) {
        plugin.logger.severe("[DreamKillEcho] Scheduled task failed: ${ex.message}")
        ex.printStackTrace()
    }
}
