package bot.poll.simple.templates

import bot.poll.simple.models.PollOption
import cats.data.NonEmptyList
import org.latestbit.slack.morphism.client.templating.SlackBlocksTemplate
import org.latestbit.slack.morphism.common.SlackActionId
import org.latestbit.slack.morphism.messages._

import java.util.UUID

class PollMessage(pollOptions: List[PollOption]) extends SlackBlocksTemplate {

  override def renderBlocks(): List[SlackBlock] = {
    blocks(
      headerBlock(
        SlackBlockPlainText(pollOptions.head.title.value)
      ),
      NonEmptyList
        .fromList(pollOptions.tail.map { pollOption =>
          button(
            text = SlackBlockPlainText(pollOption.title.value),
            action_id = SlackActionId(pollOption.title.`type` + UUID.randomUUID())
          )
        })
        .map { nonEmptyList =>
          actionsBlock(
            elements = nonEmptyList
          )
        }
        .get
    )
  }
}
