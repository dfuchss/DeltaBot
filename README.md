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

Optionally you can already specify group channels for listening or the default admin users:
```
docker run -d \
    --env DiscordToken="Your Discord Token" \
    --env Channels="ID1;ID2" \
    --env Admins="Dominik,0292;UserName2,Dsc2" \
deltabot
 ```


