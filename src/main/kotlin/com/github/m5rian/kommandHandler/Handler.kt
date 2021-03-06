@file:Suppress("unused")

package com.github.m5rian.kommandHandler

import com.github.m5rian.kommandHandler.commandInfo.Command
import com.github.m5rian.kommandHandler.resolvers.Arg
import com.github.m5rian.kommandHandler.resolvers.Resolvers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.reflections.Reflections
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

class Handler : ListenerAdapter() {
    private val cogs: MutableList<Cog> = mutableListOf()

    lateinit var coroutineScope: CoroutineScope
    var commandPackage: String? = null
    var defaultPrefixes: MutableList<String> = mutableListOf()
    var guildPrefixes: (suspend (Guild) -> MutableList<String>)? = null
    var ignoreSystemMessages: Boolean = false
    var ignoreBotMessages: Boolean = false

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        coroutineScope.launch { handle(event) }
    }

    private suspend fun handle(event: GuildMessageReceivedEvent) {
        if (ignoreSystemMessages && (event.message.isWebhookMessage || event.author.isSystem)) return
        if (ignoreBotMessages && event.author.isBot) return
        if (event.member == null) {
            // TODO Handle null members
            throw Exception("The member is null... Sorry lol")
        }
        val member = event.member!!

        val prefixes = guildPrefixes?.invoke(event.guild) ?: defaultPrefixes
        val prefix: String = prefixes.firstOrNull { event.message.contentRaw.startsWith(it) } ?: return

        this.cogs.flatMap { it.commands }.forEach { command ->
            val messageWithoutPrefix: String = event.message.contentRaw.substring(prefix.length)

            val executor: String = mutableListOf(command.name, *command.aliases)
                .filter { it.length <= messageWithoutPrefix.length }
                .firstOrNull { it.equals(messageWithoutPrefix.substring(0, it.length), ignoreCase = true) } ?: return@forEach

            val commandArguments: String? = if (messageWithoutPrefix.length == executor.length) null else messageWithoutPrefix.substring(executor.length + 1)
            val args: MutableList<String> = commandArguments?.split("\\s+".toRegex())?.toMutableList() ?: mutableListOf()
            args.removeAll { it.isBlank() }

            val cog: Cog = this.cogs.first { command in it.commands }
            coroutineScope.launch {
                try {
                    val ctx = CommandContext(event, command.method, command, executor, member)

                    val resolvedArgs: List<Arg<*>> = command.method.valueParameters
                        .subList(1, command.method.valueParameters.size) // Don't resolve CommandContext parameter
                        .mapIndexed { index, parameter ->
                            Resolvers.resolve(ctx, parameter, args.getOrNull(index), commandArguments)
                        }

                    command.method.callSuspend(cog, ctx, *resolvedArgs.toTypedArray())
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadCogs() {
        if (this.commandPackage == null) throw IllegalStateException("Command package is not set!")
        if (!this::coroutineScope.isInitialized) throw IllegalStateException("No coroutine scope is set!")

        Reflections(commandPackage).getSubTypesOf(Cog::class.java)
            .filter { !it.isInterface }
            .map { it.kotlin.objectInstance }
            .forEach { cog ->
                if (cog == null) throw IllegalStateException("Make sure all command listeners are objects!")
                this.cogs.add(cog)
                loadCommands(cog)
            }
    }

    private fun loadCommands(cog: Cog) {
        cog::class.functions.filter { it.hasAnnotation<Command>() }
            .mapNotNull { loadCommand(it) }
            .let { cog.commands.addAll(it) }
    }

    private fun loadCommand(method: KFunction<*>): CommandImpl? {
        if (method.valueParameters.firstOrNull()?.type?.classifier != CommandContext::class) return null
        val commandInfo = method.findAnnotation<Command>() ?: return null
        return CommandImpl(commandInfo.name, commandInfo.aliases, commandInfo.args, commandInfo.description, method)
    }
}

fun handler(handler: Handler.() -> Unit) = Handler().apply(handler)