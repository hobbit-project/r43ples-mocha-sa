FROM maven:3.5-jdk-8

RUN git clone -b develop https://github.com/plt-tud/r43ples.git /r43ples

WORKDIR /r43ples

RUN mvn package -DskipTests

RUN mkdir -p /r43ples/database/dataset

ADD target/r43ples-1.0.0.jar /versioning.jar

COPY scripts /r43ples
COPY conf/ /r43ples

CMD ["/r43ples/run.sh"]
