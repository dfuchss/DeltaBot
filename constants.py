SYSTEM_COMMAND_SYMBOL = "\\"
"""The symbol indicator for system commands"""

USER_COMMAND_SYMBOL = "/"
"""The symbol indicator for user commands"""

DAYS = [
    (r"\bheute\b", 0, "heute"),
    (r"(\bmorgen\b|\bin einem tag\b|\bin 1 tag\b)", 1, "morgen"),
    (r"(\bübermorgen\b|\bin zwei tagen\b|\bin 2 tagen\b)", 2, "übermorgen")
]
"""
A definition of regexes for special days (e.g. "heute" or "morgen") as triple:
(regex, offset_to_today, default_value)
"""
