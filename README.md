# DeltaBot - My simple Discord Bot (Version 2)

**Important:**

* Since discord.py will be discontinued, I've recreated DeltaBot using Kotlin and JDA. You can find the old version of DeltaBot [here](https://github.com/dfuchss/DeltaBot/releases/tag/v1.0) and its
  source code [here](./legacy)
* **I've disabled any code regarding dialogs so far, because I have to rewrite it from scratch.**

This repo contains my first [Discord](https://discordapp.com/) bot. It uses [RASA]("https://rasa.com") for NLP. The NLU is located [here](https://github.com/dfuchss/DeltaBot-NLU).

## Requirements (Development):

* maven 3 and Java 11
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
* Start the Bot: `docker-compose up -d`

