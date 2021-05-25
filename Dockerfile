FROM python:3.8-slim as builder

WORKDIR /usr/src/app
COPY ./requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ./rasa/ ./
RUN rasa telemetry disable
RUN rasa train nlu --nlu ./training.yml

FROM python:3.8-slim

ENV DiscordToken "The_Discord_Token"

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
RUN rasa telemetry disable

COPY ./ ./
COPY --from=builder /usr/src/app/models ./rasa/models

CMD ["python", "./deltabot.py"]
