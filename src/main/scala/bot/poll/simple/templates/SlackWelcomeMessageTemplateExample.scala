package bot.poll.simple.templates

import java.time.Instant
import org.latestbit.slack.morphism.client.templating._
import org.latestbit.slack.morphism.common.{SlackActionId, SlackUserId}
import org.latestbit.slack.morphism.messages.SlackBlock

class SlackWelcomeMessageTemplateExample(userId: SlackUserId) extends SlackMessageTemplate {

  override def renderPlainText(): String =
    s"Hey ${formatSlackUserId(userId)}"

  override def renderBlocks(): Option[List[SlackBlock]] =
    blocks(
      sectionBlock(
        text = md"Hey ${formatSlackUserId(userId)}"
      ),
      dividerBlock(),
      contextBlock(
        blockElements(
          md"This is an example of block message",
          md"Current time is: ${formatDate(Instant.now(), SlackTextFormatters.SlackDateTimeFormats.DateLongPretty)}"
        )
      ),
      dividerBlock(),
      imageBlock(image_url = "https://www.gstatic.com/webp/gallery3/2_webp_ll.png", alt_text = "Test Image"),
      actionsBlock(
        blockElements(
          button(text = pt"Simple", action_id = SlackActionId("simple-message-button"))
        )
      )
    )

}
