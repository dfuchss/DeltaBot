# DeltaBot - My simple Discord Bot (Version 2)

**Important:**

*Since discord.py will be discontinued, I've recreated DeltaBot using Kotlin and JDA. You can find the old version of DeltaBot [here](https://github.com/dfuchss/DeltaBot/releases/tag/v1.0)*

This repo contains my first [Discord](https://discordapp.com/) bot. It uses [RASA]("https://rasa.com") for NLP. The NLU is located [here](https://github.com/dfuchss/DeltaBot-NLU).

## Commands (Admins + Users)

For details take a look at [commands.md](./commands.md)

```
/roles: manage the role changer message of this guild
→ init: creates the role changer message in this channel
→ add: adds an emoji for a specific role
→ del: remove an emoji from the role changer message
→ purge: remove the whole message from the guild

/server-roles: manage server roles & channels
→ add: add a new role with text and voice channel
→ del: remove role and connected text and voice channels


/summon: summon players and make a poll to play a game
/teams: create a team based on the people in your voice channel

/poll: simple polls
→ new: creates a new poll
→ del: delete the poll
→ add-option: add a new option to the poll
→ del-option: remove an option to the poll
→ state: shows the status of the poll in a hidden message
→ show: shows the poll in the channel (changes will not be possible anymore)

/poll-weekday: create a poll that has the weekdays as options

/reminder: create reminders for certain times

/echo: Simply Echo the text you are writing now ..
/roll: roll a dice
/erase: erase all content of a channel
/language: set your bot language
/guild-language: set the bot language of your guild
```

## Requirements (Development):

* maven 3 and Java 16
    * `mvn clean package`
* Python 3.8 with pip (NLU only)
    * `pip install -r rasa/requirements.txt`

## Run the Bot (via Docker)

You'll need the following two things:

* The possibility to run docker containers
* A Discord Bot Account (and of course the Token)

To start the bot simply run:

* Create volume mappings (see dockerfile)
* Store token to environment: `echo "DISCORD_TOKEN=YOUR-Discord-TOKEN" > .env`
* Change the config urls to the internal docker container urls (e.g. `http://deltabot-nlu:5005`)
* Start the Bot: `docker-compose up -d`
