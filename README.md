# DeltaBot - My simple Discord Bot
This repo contains my first [Discord](https://discordapp.com/) bot. It uses [RASA]("https://rasa.com") for NLP.

## Requirements (Development):
- python3 with pip
- ffmpeg executable in path
- opus lib in library path

## Run the Bot (via Docker)
You'll need the following two things:
* The possibility to run a docker container
* A Discord Bot Account

Then simply run ``docker build -t deltabot .`` to build a docker image of the bot.
 
To start the bot simply run:
```
docker run -d \
    --env DiscordToken="Your Discord Token" \
deltabot
 ```

You might also want to access / store the configuration. Therefore, you can use the `--env CONF_FILE=/path/you/want -v MachinePath/VolumePath`


