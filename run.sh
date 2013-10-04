#!/bin/sh
#
# sample script to run spirebot

# we have to ask for UTF-8 in so many places
export LCLANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

ASSEMBLY=target/scala-2.10/spirebot-assembly-0.6.jar

if [ ! -r $ASSEMBLY ]; then
    echo "couldn't find $ASSEMBLY"
    echo "did you run 'sbt assembly' yet?"
    exit 1
fi

java \
 -Dnick=spirebot__ \
 -Downers=d_m \
 -Dserver=irc.freenode.net \
 -Dchannels='#spirebot-test' \
 -Dsun.jnu.encoding=UTF-8 \
 -Dfile.encoding=UTF-8 \
 -cp $ASSEMBLY \
 spirebot.Spirebot
