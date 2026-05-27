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
    fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit): SchedulerTask
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

interface SchedulerTask {
    fun cancel()
}

private class BukkitSchedulerAdapter(private val plugin: Plugin) : SchedulerAdapter {
    private val periodicTasks = Collections.synchronizedList(mutableListOf<SchedulerTask>())
    override val platformName: String = "BukkitScheduler"

    override fun runMain(task: () -> Unit) {
        if (Bukkit.isPrimaryThread()) task() else Bukkit.getScheduler().runTask(plugin, Runnable { task.safeRun(plugin) })
    }

    override fun runAsync(task: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { task.safeRun(plugin) })
    }

    override fun runLater(delayTicks: Long, task: () -> Unit) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { task.safeRun(plugin) }, delayTicks)
    }

    override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit): SchedulerTask {
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { task.safeRun(plugin) }, delayTicks, periodTicks)
        return BukkitTaskHandle { bukkitTask.cancel() }.also { periodicTasks += it }
    }

    override fun runEntity(entity: Entity, task: () -> Unit) = runMain(task)

    override fun runLocation(location: Location, task: () -> Unit) = runMain(task)

    override fun cancelAll() {
        val copy = periodicTasks.toList()
        periodicTasks.clear()
        copy.forEach { it.cancel() }
        Bukkit.getScheduler().cancelTasks(plugin)
    }
}

private class FoliaReflectiveSchedulerAdapter(private val plugin: Plugin) : SchedulerAdapter {
    override val platformName: String = "FoliaReflectiveScheduler"
    private val periodicTasks = Collections.synchronizedList(mutableListOf<SchedulerTask>())

    override fun runMain(task: () -> Unit) {
        invokeGlobal("run", task)
    }

    override fun runAsync(task: () -> Unit) {
        val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
        val method = scheduler.javaClass.methods.first { method ->
            method.name == "runNow" && method.parameterTypes.size == 2
        }
        method.invoke(scheduler, plugin, Consumer<Any> { task.safeRun(plugin) })
    }

    override fun runLater(delayTicks: Long, task: () -> Unit) {
        invokeGlobal("runDelayed", task, delayTicks)
    }

    override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit): SchedulerTask {
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
        return ReflectiveTaskHandle(scheduled).also { periodicTasks += it }
    }

    override fun runEntity(entity: Entity, task: () -> Unit) {
        val entityScheduler = entity.javaClass.getMethod("getScheduler").invoke(entity)
        val run = entityScheduler.javaClass.methods.first { method ->
            method.name == "run" && method.parameterTypes.size == 3
        }
        run.invoke(entityScheduler, plugin, Consumer<Any> { task.safeRun(plugin) }, Runnable {})
    }

    override fun runLocation(location: Location, task: () -> Unit) {
        val scheduler = Bukkit::class.java.getMethod("getRegionScheduler").invoke(null)
        val method = scheduler.javaClass.methods.first { method ->
            method.name == "run" && method.parameterTypes.size == 5
        }
        method.invoke(
            scheduler,
            plugin,
            location.world,
            location.blockX shr 4,
            location.blockZ shr 4,
            Consumer<Any> { task.safeRun(plugin) }
        )
    }

    override fun cancelAll() {
        val copy = periodicTasks.toList()
        periodicTasks.clear()
        copy.forEach { it.cancel() }
    }

    private fun invokeGlobal(methodName: String, task: () -> Unit, delayTicks: Long? = null) {
        val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
        val candidates = scheduler.javaClass.methods.filter { it.name == methodName }
        val method = if (delayTicks == null) {
            candidates.first { it.parameterTypes.size == 2 }
        } else {
            candidates.first { it.parameterTypes.size == 3 }
        }
        if (delayTicks == null) {
            method.invoke(scheduler, plugin, Consumer<Any> { task.safeRun(plugin) })
        } else {
            method.invoke(scheduler, plugin, Consumer<Any> { task.safeRun(plugin) }, delayTicks)
        }
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

private class BukkitTaskHandle(private val cancelAction: () -> Unit) : SchedulerTask {
    override fun cancel() {
        runCatching { cancelAction() }
    }
}

private class ReflectiveTaskHandle(private val scheduled: Any?) : SchedulerTask {
    override fun cancel() {
        val task = scheduled ?: return
        runCatching { task.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterCount == 0 }?.invoke(task) }
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
