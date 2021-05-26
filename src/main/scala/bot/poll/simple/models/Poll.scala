package bot.poll.simple.models

import io.circe.generic.JsonCodec

@JsonCodec final case class PollOption(title: PollField)
@JsonCodec final case class PollField(`type`: String, value: String)
