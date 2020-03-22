FROM python:3.6

ENV TTSKey "The_TTS_Key"
ENV TTSResource "The_TTS_Resource"
ENV DiscordToken "The_Discord_Token"
ENV Channels ""
ENV Admins ""

WORKDIR /usr/src/

# FFMPEG
RUN wget https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz
RUN tar xJf ffmpeg-release-amd64-static.tar.xz
RUN rm ffmpeg-release-amd64-static.tar.xz
RUN mv ffmpeg* ffmpeg
RUN ln -s /usr/src/ffmpeg/ffmpeg /usr/bin/ffmpeg

# OPUS
RUN apt update && apt install libopus0 opus-tools -y && apt clean

WORKDIR /usr/src/app

COPY ./requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ./ ./

WORKDIR /usr/src/app/rasa
RUN rasa train nlu --nlu ./training.md

WORKDIR /usr/src/app
CMD ["python", "./deltabot.py"]
