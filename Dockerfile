FROM mcr.microsoft.com/playwright:v1.42.1-jammy

WORKDIR /app

RUN curl -sLO https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
RUN chmod +x linux-install.sh
RUN ./linux-install.sh
RUN apt update
RUN apt install -y openjdk-21-jdk
RUN curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --to /usr/bin

COPY . /app

ENTRYPOINT ["/usr/bin/just", "spec"]
