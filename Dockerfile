FROM python:3.9.2

ENV DiscordToken "The_Discord_Token"
ENV Channels ""
ENV Admins ""

# WORKDIR /usr/src/

# FFMPEG (Voice)
# RUN wget https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz
# RUN tar xJf ffmpeg-release-amd64-static.tar.xz
# RUN rm ffmpeg-release-amd64-static.tar.xz
# RUN mv ffmpeg* ffmpeg
# RUN ln -s /usr/src/ffmpeg/ffmpeg /usr/bin/ffmpeg

# OPUS (Voice)
# RUN apt update && apt install libopus0 opus-tools -y && apt clean

WORKDIR /usr/src/app

COPY ./requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

WORKDIR /usr/src/app/rasa
COPY ./rasa/ ./
RUN rasa train nlu --nlu ./training.md

WORKDIR /usr/src/app
COPY ./ ./
CMD ["python", "./deltabot.py"]
