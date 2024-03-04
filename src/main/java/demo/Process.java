/**
 * @file Process.java
 * @brief File containing the implementation of the actors
*/
package demo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.Random;

import demo.Main.*;

/**
 * @class Process
 * @brief Class representing the actor
*/
public class Process extends AbstractActor {
	final static boolean DEBUG = false;
	final static double CRASH_PROBABILITY = 0.1; // Probability of crashing, alpha
	
	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	private int id, N;
	private ActorRef[] actors;
	private boolean launched = false, shouldCrash = false, crashed = false, hold = false;

	private int ballot, proposal, readBallot, imposeBallot, estimate;
	private Pair[] states;

	private int receivedStates = 0;
	private boolean biggerThanHalf = false;

	private int ACKnum = 0;
	private boolean ACKconfirmed = false;

	private int proposeResult = -2;

	private long startTime = 0;
	private long endTime = 0;

	/**
	 * @brief Initializes the actor
	 * @param id The unique identifier of the actor
	*/
	public Process(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public int getProposeResult() {
		return proposeResult;
	}

	/**
	 * @brief Static function creating actor
	 * @param id The unique identifier of the actor
	 * @return
	*/
	public static Props createActor(int id) {
		return Props.create(Process.class, () -> {
			return new Process(id);
		});
	}

	/**
	 * @brief Creates the actor's behavior
	 * @return
	*/
	@Override
	public Receive createReceive() {
		return receiveBuilder()
			.match(ActorinfoMessage.class, this::receiveActorinfoMessage)
			.match(LaunchMessage.class, this::receiveLaunchMessage)
			.match(CrashMessage.class, this::receiveCrashMessage)
			.match(HoldMessage.class, this::receiveHoldMessage)
			.match(ReadMessage.class, this::receiveReadMessage)
			.match(AbortMessage.class, this::receiveAbortMessage)
			.match(GatherMessage.class, this::receiveGatherMessage)
			.match(ImposeMessage.class, this::receiveImposeMessage)
			.match(ACKMessage.class, this::receiveACKMessage)
			.match(DecideMessage.class, this::receiveDecideMessage)
			.build();
	}

	/**
	 * @brief Proposes a value
	 * @param v The value to propose
	*/
	void propose (int v) {
		if (crashed) return;
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] proposed value ["+v+"]");
		}
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			proposal = v;
			ballot += N;
			for (int i = 0; i < N; i++) {
				states[i].first = 0;
				states[i].second = 0;
			}
			ReadMessage readMessage = new ReadMessage(ballot);
			for (int i = 0; i < N; i++) {
				actors[i].tell(readMessage, getSelf());
			}
		}
	}

	public void receiveActorinfoMessage (ActorinfoMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received ACTORINFO message from ["+ getSender().path().name() +"]");
		}
		this.actors = m.actors;
		this.N = m.length;
		ballot = id - N;
		proposal = 0;
		readBallot = 0;
		imposeBallot = id - N;
		estimate = 0;
		states = new Pair[N];
		for (int i = 0; i < N; i++) {
			states[i] = new Pair(0, 0);
		}
		crashed = false;
		hold = false;
		receivedStates = 0;
	}

	public void receiveLaunchMessage (LaunchMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received LAUNCH message from ["+ getSender().path().name() +"]");
		}
		if (!this.launched) {
			this.launched = true;
			startTime = System.currentTimeMillis();
			Random rand = new Random();
			int proposedNumber = rand.nextInt(2);

			// Propose a value
			propose(proposedNumber);
		}
	}

	public void receiveCrashMessage (CrashMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received CRASH message from ["+ getSender().path().name() +"]");
		}
		this.shouldCrash = true;
	}

	public void receiveHoldMessage (HoldMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received HOLD message from ["+ getSender().path().name() +"]");
		}
		hold = true;
	}

	public void receiveReadMessage (ReadMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received READ message from ["+ getSender().path().name() +"] with ballot ["+m.ballot+"]");
		}
		if (crashed) return;
		if (proposeResult >= 0) return;
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			if (readBallot > m.ballot || imposeBallot > m.ballot) {
				getSender().tell(new AbortMessage(m.ballot), getSelf());
			}
			else {
				readBallot = m.ballot;
				getSender().tell(new GatherMessage(m.ballot, imposeBallot, estimate), getSelf());
			}
		}
	}

	public void receiveAbortMessage (AbortMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received ABORT message from ["+ getSender().path().name() +"] with ballot ["+m.ballot+"]");
		}
		if (crashed) return;
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			proposeResult = -1;
			if (!hold) { // Propose Again
				propose(proposal);
			}
		}
	}

	public void receiveGatherMessage (GatherMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received GATHER message from ["+ getSender().path().name() +"] with ballot ["+m.ballot+"] and imposeBallot ["+m.imposeBallot+"] and estimate ["+m.estimate+"]");
		}
		if (crashed) return;
		if (proposeResult >= 0) return;
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			states[(m.ballot + N) % N].first = m.estimate;
			states[(m.ballot + N) % N].second = m.imposeBallot;
			receivedStates++;
			if (receivedStates > N/2 && !biggerThanHalf) {
				biggerThanHalf = true;
				int maxidx = -1;
				for (int i = 0; i < N; i++) {
					if (states[i].second > 0) {
						if (maxidx == -1 || states[i].second > states[maxidx].second) {
							maxidx = i;
						}
					}
				}
				if (maxidx != -1) proposal = states[maxidx].first;
				for (int i = 0; i < N; i++) {
					states[i].first = 0;
					states[i].second = 0;
				}
				receivedStates = 0;
				biggerThanHalf = false;
				for (int i = 0; i < N; i++) {
					actors[i].tell(new ImposeMessage(ballot, proposal), getSelf());
				}
			}
		}
	}

	public void receiveImposeMessage (ImposeMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received IMPOSE message from ["+ getSender().path().name() +"] with ballot ["+m.ballot+"] and proposal ["+m.proposal+"]");
		}
		if (crashed) return;
		if (proposeResult >= 0) return;
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			if (readBallot > m.ballot || imposeBallot > m.ballot) {
				getSender().tell(new AbortMessage(m.ballot), getSelf());
			}
			else {
				estimate = m.proposal;
				imposeBallot = m.ballot;
				getSender().tell(new ACKMessage(m.ballot), getSelf());
			}
		}
	}

	public void receiveACKMessage (ACKMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received ACK message from ["+ getSender().path().name() +"] with ballot ["+m.ballot+"]");
		}
		if (crashed) return;
		if (proposeResult >= 0) return;
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			ACKnum++;
			if (ACKnum > N/2 && !ACKconfirmed) {
				ACKconfirmed = true;
				endTime = System.currentTimeMillis();
				log.info("Total time for the Process ["+id+"] to decide: " + (endTime - startTime) + "ms");
				for (int i = 0; i < N; i++) {
					actors[i].tell(new DecideMessage(proposal), getSelf());
				}
			}
		}
	}

	public void receiveDecideMessage (DecideMessage m) {
		if (DEBUG) {
			log.info("["+getSelf().path().name()+"] received DECIDE message from ["+ getSender().path().name() +"] with proposal ["+m.proposal+"]");
		}
		if (crashed) return;
		if (shouldCrash) {
			double r = Math.random();
			if (r < CRASH_PROBABILITY) {
				if (DEBUG) {
					log.info("["+getSelf().path().name()+"] crashed");
				}
				crashed = true;
			}
		}
		if (!crashed) {
			proposeResult = m.proposal;
		}
	}

	public class Pair {
		public int first, second;

		public Pair(int first, int second) {
			this.first = first;
			this.second = second;
		}
	}
}
