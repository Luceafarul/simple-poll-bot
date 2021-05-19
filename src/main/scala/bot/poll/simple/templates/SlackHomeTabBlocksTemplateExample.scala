package bot.poll.simple.templates

import java.time.Instant
import org.latestbit.slack.morphism.client.templating.SlackBlocksTemplate
import org.latestbit.slack.morphism.common.{SlackActionId, SlackUserId}
import org.latestbit.slack.morphism.messages.SlackBlock

case class SlackHomeNewsItem(title: String, body: String, published: Instant)

class SlackHomeTabBlocksTemplateExample(latestNews: List[SlackHomeNewsItem], userId: SlackUserId)
    extends SlackBlocksTemplate {

  override def renderBlocks(): List[SlackBlock] =
    blocks(
      blocks(
        sectionBlock(
          text = md"Home tab for ${formatSlackUserId(userId)}"
        ),
        contextBlock(
          blockElements(
            md"This is an example of Slack Home Tab",
            md"Last updated: ${formatDate(Instant.now())}"
          )
        ),
        dividerBlock(),
        imageBlock(image_url = "https://www.gstatic.com/webp/gallery/4.png", alt_text = "Test Image"),
        actionsBlock(
          blockElements(
            button(text = pt"Simple", action_id = SlackActionId("simple-home-button"))
          )
        )
      ),
      optBlocks(latestNews.nonEmpty)(
        sectionBlock(
          text = md"*Latest news:*"
        ),
        dividerBlock(),
        latestNews.map { news =>
          blocks(
            sectionBlock(
              text = md" • *${news.title}*\n${formatSlackQuoteText(news.body)}"
            ),
            contextBlock(
              blockElements(
                md"*Published*",
                md"${formatDate(news.published)}"
              )
            )
          )
        }
      )
    )
}
