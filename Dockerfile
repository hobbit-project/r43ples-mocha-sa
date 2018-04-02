FROM maven:3.5-jdk-8

RUN git clone -n -b develop https://github.com/plt-tud/r43ples.git /r43ples && cd /r43ples && git checkout a4022c2dddab6c02a56dcaf1697d0848e84f9e82
#RUN git clone -b develop https://github.com/plt-tud/r43ples.git /r43ples

WORKDIR /r43ples

RUN mvn package -DskipTests

RUN mkdir -p database/dataset

ADD target/r43ples-1.0.0.jar versioning.jar

ADD scripts/ scripts
ADD conf/ conf

CMD java -Xmx16G -cp versioning.jar org.hobbit.core.run.ComponentStarter eu.hobbit.mocha.systems.r43ples.R43plesSystemAdapter
