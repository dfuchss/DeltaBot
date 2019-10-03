# DeltaBot - My simple Discord Bot
This repo contains my first [Discord](https://discordapp.com/) bot. It uses [RASA]("https://rasa.com") for NLP and [Microsoft Speech Services](https://docs.microsoft.com/en-us/azure/cognitive-services/speech-service/) for Voice Synthesis.

## Requirements (Development):
- python3 with pip
- ffmpeg executable in path
- opus lib in library path

## Run the Bot (via Docker)
You'll need the following three things:
* The possibility to run a docker container
* A Discord Bot Account
* A subscription for the Speech Services on Azure

Then simply run ``docker build -t deltabot .`` to build a docker image of the bot.
 
To start the bot simply run:
```
docker run -d \
    --env TTSKey="Your TTS Key" \
    --env TTSResource="Your TTS Resource" \
    --env DiscordToken="Your Discord Token" \
deltabot
 ```
