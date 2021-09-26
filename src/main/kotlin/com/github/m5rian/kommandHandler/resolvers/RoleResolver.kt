package com.github.m5rian.kommandHandler.resolvers

import com.github.m5rian.kommandHandler.CommandContext
import com.github.m5rian.kommandHandler.DiscordRegex
import net.dv8tion.jda.api.entities.Role

class RoleResolver : Resolver<Role> {
    override suspend fun resolveIfNotNull(ctx: CommandContext, parameter: String): Arg<Role> {
        return when {
            parameter.matches(DiscordRegex.id) || parameter.matches(DiscordRegex.roleMention) -> {
                val id = parameter.removePrefix("<@&").removeSuffix(">")
                Arg.ofNullable(ctx.guild.getRoleById(id))
            }
            else -> Arg.ofNullable(ctx.guild.roles.find { it.name.contains(parameter, ignoreCase = true) })
        }
    }
}