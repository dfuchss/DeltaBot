def __crop_command(raw_message: str) -> str:
    """
    Delete the command part from a message (e.g. "/help xy" -> "xy")

    :param raw_message: the message text
    :return: the message text without the command part
    """
    msg = raw_message.split(" ", 1)
    if len(msg) == 1:
        return ""
    return msg[1].strip()


def __read_number_param(text: str, default: int) -> int:
    """
    Read exactly one number parameter from the command (e.g. "/roll 5" -> 5)

    :param text: the input text
    :param default: the default value if no number has been found
    :return: the detected int
    """
    val = default
    split = text.strip().split(" ")
    if len(split) == 2:
        try:
            val = int(split[1])
            if val < 1:
                val = default
        except Exception:
            pass
    return val
