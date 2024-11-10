package si.ogrodje.ashhook

import jakarta.mail.Flags.Flag

enum SafeFlags:
  case Seen
  case Flagged
  case User
  case Draft
  case Answered
  case Deleted
  case Recent
  case Unknown(f: Flag)

object FlagOps:
  import SafeFlags.*

  extension (f: Flag)
    def asSafeFlag: SafeFlags = f match
      case Flag.SEEN     => Seen
      case Flag.FLAGGED  => Flagged
      case Flag.USER     => User
      case Flag.DRAFT    => Draft
      case Flag.ANSWERED => Answered
      case Flag.DELETED  => Deleted
      case Flag.RECENT   => Recent
      case x             => Unknown(x)
