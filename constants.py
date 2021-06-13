SYSTEM_COMMAND_SYMBOL = "\\"
USER_COMMAND_SYMBOL = "/"

DAYS = [
    (r"\bheute\b", 0, "heute"),
    (r"(\bmorgen\b|\bin einem tag\b|\bin 1 tag\b)", 1, "morgen"),
    (r"(\bübermorgen\b|\bin zwei tagen\b|\bin 2 tagen\b)", 2, "übermorgen")
]
