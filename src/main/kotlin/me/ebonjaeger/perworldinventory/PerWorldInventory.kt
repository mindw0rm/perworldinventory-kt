package me.ebonjaeger.perworldinventory

import ch.jalu.configme.migration.PlainMigrationService
import ch.jalu.configme.resource.YamlFileResource
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.ebonjaeger.perworldinventory.configuration.MetricsSettings
import me.ebonjaeger.perworldinventory.configuration.PlayerSettings
import me.ebonjaeger.perworldinventory.configuration.PluginSettings
import me.ebonjaeger.perworldinventory.configuration.Settings
import net.milkbowl.vault.economy.Economy
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileWriter
import java.nio.file.Files

class PerWorldInventory : JavaPlugin()
{

    var economy: Economy? = null
        private set
    // TODO: Also check setting
    var econEnabled = economy != null

    val DATA_DIRECTORY = File(dataFolder, "data")
    val WORLDS_CONFIG_FILE = File(dataFolder, "worlds.json")

    private val groupManager = GroupManager(this)

    override fun onEnable()
    {
        ConsoleLogger.setLogger(logger)

        // Make data folders
        val defaultsDir = File(DATA_DIRECTORY, "defaults").toPath()
        if (Files.exists(defaultsDir))
        {
            Files.createDirectories(defaultsDir)
        }

        // Check if `worlds.yml` exists. If it does, convert it to JSON.
        // Otherwise, save it if it doesn't exist.
        if (Files.exists(File(dataFolder, "worlds.yml").toPath()))
        {
            val configuration = YamlConfiguration.loadConfiguration(File(dataFolder, "worlds.yml"))
            convertYamlToJson(configuration)
        } else
        {
            saveResource("worlds.json", false)
        }


        val settings = Settings(YamlFileResource(File(dataFolder, "config.yml")),
                PlainMigrationService(),
                PluginSettings::class.java,
                MetricsSettings::class.java,
                PlayerSettings::class.java)

        ConsoleLogger.setUseDebug(settings.getProperty(PluginSettings.DEBUG_MODE))

        groupManager.loadGroups(WORLDS_CONFIG_FILE)

        // TODO: Register commands

        // TODO: Register Listeners

        // Register Vault if present
        if (server.pluginManager.getPlugin("Vault") != null)
        {
            ConsoleLogger.info("Vault found! Hooking into it...")
            val rsp = server.servicesManager.getRegistration(Economy::class.java)
            if (rsp != null)
            {
                economy = rsp.provider
                ConsoleLogger.info("Hooked into Vault!")
            } else
            {
                ConsoleLogger.warning("Unable to hook into Vault!")
            }
        }

        // TODO: Initialize bStats telemetry

        ConsoleLogger.debug("PerWorldInventory is enabled and debug-mode is active!");
    }

    override fun onDisable()
    {
        groupManager.groups.clear()
        server.scheduler.cancelTasks(this)
    }

    /**
     * Converts an existing YAML worlds configuration to its representation
     * in JSON.
     *
     * The old file will be renamed (not deleted!), and a new json file
     * created and written to.
     *
     * @param config The Yaml worlds configuration
     */
    private fun convertYamlToJson(config: FileConfiguration)
    {
        val root = JsonObject()
        val groups = JsonObject()

        config.getConfigurationSection("groups.").getKeys(false).forEach {
            val group = JsonObject()

            // Convert the list of worlds
            val worlds = config.getStringList("groups.$it.worlds")
            val worldsArray = JsonArray()
            worlds.forEach { worldsArray.add(it) }
            group.add("worlds", worldsArray)

            // Convert the default gamemode
            group.addProperty("default-gamemode", config.getString("groups" +
                    ".$it.default-gamemode"))

            groups.add(it, group)
        }

        root.add("groups", groups)

        // Rename old .yml file, and create new json file
        Files.move(File(dataFolder, "worlds.yml").toPath(), File(dataFolder,
                "worlds.old.yml").toPath())
        Files.createFile(WORLDS_CONFIG_FILE.toPath())

        // Save to the new json file
        val gson = Gson()
        server.scheduler.runTaskAsynchronously(this, {
            FileWriter(WORLDS_CONFIG_FILE).use {
                it.write(gson.toJson(root))
            }
        })
    }
}