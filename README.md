# DeltaBot - My simple Discord Bot

**Important:**

*Since discord.py will be discontinued, this commit will be the last maintenance commit of the DeltaBot using python. I
will create a reimplementation of the bot and merge it as soon as the bot is usable.*

This repo contains my first [Discord](https://discordapp.com/) bot. It uses [RASA]("https://rasa.com") for NLP. The NLU
is located [here](https://github.com/dfuchss/DeltaBot-NLU).

## Requirements (Development):

* Python 3.8 with pip
* `pip install -r requirements.txt`
* `pip install -r rasa/requirements.txt`

## Run the Bot (via Docker)

You'll need the following two things:

* The possibility to run docker containers
* A Discord Bot Account (and of course the Token)

To start the bot simply run:

* Create empty configuration: `touch config.json`
* Store token to environment: `echo "DiscordToken=YOUR-Discord-TOKEN" > .env`
* Start the Bot: `docker-compose up -d`

