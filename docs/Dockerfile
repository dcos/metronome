FROM buildpack-deps:stretch-curl

COPY Gemfile Gemfile.lock /jekyll/

RUN apt-get update && \
  curl -sL https://deb.nodesource.com/setup_6.x | bash - && \
  apt-get install -y gcc git libxml2 zlib1g-dev libxml2-dev ruby ruby-dev make autoconf nodejs python python-dev libssl-dev libevent-dev build-essential locales && \
  gem install bundler && \
  cd /jekyll && bundle install && \
  apt-get purge -y gcc ruby-dev python-dev && \
  apt-get -y autoremove && \
  rm -rf /var/lib/apt/lists

# Install program to configure locales
RUN dpkg-reconfigure locales && \
  locale-gen C.UTF-8 && \
  /usr/sbin/update-locale LANG=C.UTF-8

# Install needed default locale for Makefly
RUN echo 'en_US.UTF-8 UTF-8' >> /etc/locale.gen && \
  locale-gen

# Set default locale for the environment
ENV LC_ALL C.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

COPY entrypoint.sh /

EXPOSE 4000
VOLUME ["/site-docs"]

ENTRYPOINT /entrypoint.sh
