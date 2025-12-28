package wowchat.commands

import com.typesafe.scalalogging.StrictLogging
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import wowchat.common.Global
import wowchat.discord.Discord
import wowchat.game.{GamePackets, GameResources, GuildInfo, GuildMember}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

case class WhoRequest(messageChannel: MessageChannel, playerName: String)
case class WhoResponse(playerName: String, guildName: String, lvl: Int, cls: String, race: String, gender: Option[String], zone: String)

object CommandHandler extends StrictLogging {

  private val NOT_ONLINE = "Bot is not online."

  // make some of these configurable
  private val trigger = "?"

  // gross. rewrite
  var whoRequest: WhoRequest = _

  // Check if member has permission to use protected commands
  private def hasProtectedPermission(fromChannel: MessageChannel, member: Option[Member]): Boolean = {
    val channelName = fromChannel.getName.toLowerCase
    val allowedChannels = Global.config.discord.protectedGuildCommandChannels
    val allowedRoles = Global.config.discord.protectedGuildCommandRoles

    // Check channel restriction
    val channelAllowed = allowedChannels.isEmpty || allowedChannels.contains(channelName)
    if (!channelAllowed) {
      return false
    }

    // Check role restriction (if roles are configured)
    if (allowedRoles.isEmpty) {
      return true // No role restriction configured
    }

    member.exists(m => {
      m.getRoles.asScala.exists(role => allowedRoles.contains(role.getName.toLowerCase))
    })
  }

  // returns back the message as an option if unhandled
  // needs to be refactored into a Map[String, <Intelligent Command Handler Function>]
  def apply(fromChannel: MessageChannel, message: String, member: Option[Member] = None): Boolean = {
    if (!Global.config.discord.enableGuildCommands && !message.startsWith(trigger)) {
      return false
    }

    if (!message.startsWith(trigger)) {
      return false
    }

    val splt = message.substring(trigger.length).split(" ")
    val possibleCommand = splt(0).toLowerCase
    val arguments = if (splt.length > 1 && splt(1).length <= 16) Some(splt(1)) else None

    def protectedCommand(commandName: String, callback: () => Option[String]): Option[String] = {
      if (hasProtectedPermission(fromChannel, member)) {
        callback()
      } else {
        Some(s"You don't have permission to use '$commandName'")
      }
    }

    Try {
      possibleCommand match {
        case "who" | "online" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(game => {
            val whoSucceeded = game.handleWho(arguments)
            if (arguments.isDefined) {
              whoRequest = WhoRequest(fromChannel, arguments.get)
            }
            whoSucceeded
          })
        case "gmotd" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(_.handleGmotd())
        case "ginvite" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(game => {
            protectedCommand("ginvite", () => {
              arguments match {
                case Some(name) =>
                  game.sendGuildInvite(name)
                  Some(s"Invited '$name' to the guild")
                case None =>
                  Some("Usage: ?ginvite <name>")
              }
            })
          })
        case "gkick" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(game => {
            protectedCommand("gkick", () => {
              arguments match {
                case Some(name) =>
                  game.sendGuildKick(name)
                  Some(s"Kicked '$name' from the guild")
                case None =>
                  Some("Usage: ?gkick <name>")
              }
            })
          })
        case "ignore" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(game => {
            protectedCommand("ignore", () => {
              arguments match {
                case Some(name) =>
                  game.sendAddIgnore(name)
                  Some(s"Added '$name' to ignore list")
                case None =>
                  Some("Usage: ?ignore <name>")
              }
            })
          })
        case "unignore" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(game => {
            protectedCommand("unignore", () => {
              arguments match {
                case Some(name) =>
                  game.sendDelIgnore(name) match {
                    case Some(error) => Some(error)
                    case None => Some(s"Removed '$name' from ignore list")
                  }
                case None =>
                  Some("Usage: ?unignore <name>")
              }
            })
          })
        case "help" =>
          Global.game.fold({
            Discord.sendMessage(fromChannel, NOT_ONLINE)
            return true
          })(_ => {
            Some("Commands: `?who [name]`, `?online`, `?gmotd`, `?help`\nProtected: `?ginvite <name>`, `?gkick <name>`, `?ignore <name>`, `?unignore <name>`")
          })
      }
    }.fold(throwable => {
      // command not found, should send to wow chat
      false
    }, opt => {
      // command found, do not send to wow chat
      if (opt.isDefined) {
        Discord.sendMessage(fromChannel, opt.get)
      }
      true
    })
  }

  // eww
  def handleWhoResponse(whoResponse: Option[WhoResponse],
                        guildInfo: GuildInfo,
                        guildRoster: mutable.Map[Long, GuildMember],
                        guildRosterMatcherFunc: GuildMember => Boolean): Iterable[String] = {
    whoResponse.map(r => {
      Seq(s"${r.playerName} ${if (r.guildName.nonEmpty) s"<${r.guildName}> " else ""}is a level ${r.lvl}${r.gender.fold(" ")(g => s" $g ")}${r.race} ${r.cls} currently in ${r.zone}.")
    }).getOrElse({
      // Check guild roster
      guildRoster
        .values
        .filter(guildRosterMatcherFunc)
        .map(guildMember => {
          val cls = new GamePackets{}.Classes.valueOf(guildMember.charClass) // ... should really move that out
          val days = guildMember.lastLogoff.toInt
          val hours = ((guildMember.lastLogoff * 24) % 24).toInt
          val minutes = ((guildMember.lastLogoff * 24 * 60) % 60).toInt
          val minutesStr = s" $minutes minute${if (minutes != 1) "s" else ""}"
          val hoursStr = if (hours > 0) s" $hours hour${if (hours != 1) "s" else ""}," else ""
          val daysStr = if (days > 0) s" $days day${if (days != 1) "s" else ""}," else ""

          val guildNameStr = if (guildInfo != null) {
            s" <${guildInfo.name}>"
          } else {
            // Welp, some servers don't set guild guid in character selection packet.
            // The only other way to get this information is through parsing SMSG_UPDATE_OBJECT
            // and its compressed version which is quite annoying especially across expansions.
            ""
          }

          s"${guildMember.name}$guildNameStr is a level ${guildMember.level} $cls currently offline. " +
            s"Last seen$daysStr$hoursStr$minutesStr ago in ${GameResources.AREA.getOrElse(guildMember.zoneId, "Unknown Zone")}."
        })
    })
  }
}
