package bot.poll.simple.templates

import org.latestbit.slack.morphism.client.templating.SlackModalViewTemplate
import org.latestbit.slack.morphism.common.SlackActionId
import org.latestbit.slack.morphism.messages._

class PollModal(initialValue: String = "") extends SlackModalViewTemplate {

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
        )
      ),
      inputBlock(
        label = pt"Option 1",
        element = SlackBlockPlainInputElement(
          action_id = SlackActionId("opt_1_action")
        )
      ),
      inputBlock(
        label = pt"Option 2",
        element = SlackBlockPlainInputElement(
          action_id = SlackActionId("opt_2_action")
        )
      )
    )

}
