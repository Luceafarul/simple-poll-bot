package bot.poll.simple.templates

import cats.data.NonEmptyList
import org.latestbit.slack.morphism.client.templating.SlackModalViewTemplate
import org.latestbit.slack.morphism.common.{SlackActionId, SlackBlockId}
import org.latestbit.slack.morphism.messages._

class PollModal(slackChannelId: String, initialValue: String = "") extends SlackModalViewTemplate {

  override def titleText(): SlackBlockPlainText = pt"Simple Poll"

  override def submitText(): Option[SlackBlockPlainText] = Some(pt"Create")

  override def closeText(): Option[SlackBlockPlainText] = Some(pt"Cancel")

  override def renderBlocks(): List[SlackBlock] =
    blocks(
      inputBlock(
        label = pt"Poll issue",
        element = SlackBlockPlainInputElement(
          action_id = SlackActionId("title"),
          initial_value = Some(initialValue),
          placeholder = SlackBlockPlainText(
            text = "Write poll issue here"
          )
        ),
        block_id = Some(SlackBlockId(slackChannelId))
      ),
      inputBlock(
        label = pt"Option 1",
        element = SlackBlockPlainInputElement(
          action_id = SlackActionId("title")
        )
      ),
      inputBlock(
        label = pt"Option 2",
        element = SlackBlockPlainInputElement(
          action_id = SlackActionId("title")
        )
      ),
      actionsBlock(
        NonEmptyList.of(
          button(
            text = SlackBlockPlainText("Add another option"),
            action_id = SlackActionId("add_another_option")
          )
        )
      )
    )

}
