FROM maven:3.5-jdk-8

RUN git clone -b develop https://github.com/plt-tud/r43ples.git /r43ples

WORKDIR /r43ples

RUN mvn package -DskipTests

RUN mkdir -p /r43ples/database/dataset

ADD target/r43ples-1.0.0.jar /versioning.jar

ADD scripts /r43ples/scripts
ADD conf/ /r43ples/conf

CMD java -cp /versioning.jar org.hobbit.core.run.ComponentStarter eu.hobbit.mocha.systems.r43ples.R43plesSystemAdapter
