package bot.poll.simple.templates

import cats.data.NonEmptyList
import org.latestbit.slack.morphism.client.templating.SlackBlocksTemplate
import org.latestbit.slack.morphism.messages._

class PollMessage(pollMessage: String, pollOptions: List[String] = ???) extends SlackBlocksTemplate {

  override def renderBlocks(): List[SlackBlock] =
    blocks(
      headerBlock(
        SlackBlockPlainText(pollMessage)
      ),
      actionsBlock(
        elements = NonEmptyList.of(
          button(
            text = ???,
            action_id = ???
          ),
          button(
            text = ???,
            action_id = ???
          ),
          button(
            text = ???,
            action_id = ???
          )
        )
      )
    )

}
