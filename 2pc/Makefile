# export CLASSPATH=${PWD}:${PWD}/../lib

# define compiler and compiler flag variables
JFLAGS = -g
JC = javac

# set necessary environment variables as well
all: Server.class UserNode.class TxnDecision.class TxnMasterRecord.class TxnMasterLog.class TxnSlaveRecord.class TxnSlaveLog.class TxnPhase.class TxnVote.class CoordinatorMsg.class ParticipantMsg.class

%.class: %.java
	$(JC) $(JFLAGS) $*.java

# format command
.PHONY: format
format:
	# clang-format all the source code files
	find *.java | sed 's| |\\ |g' | xargs clang-format -style=google -i

# submission helper command
.PHONY: submit
submit:
	# submit by compressing tar
	tar cvzf ../mysolution.tgz design.pdf Makefile Server.java UserNode.java TxnDecision.java TxnMasterRecord.java TxnMasterLog.java TxnSlaveRecord.java TxnSlaveLog.java TxnPhase.java TxnVote.java CoordinatorMsg.java ParticipantMsg.java

# clean up command
.PHONY: clean
clean:
	# remove all the files with extension .class and executable with tar
	rm -f *.class ../mysolution.tgz