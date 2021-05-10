package geniusweb.sampleagent;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllPartialBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.inform.ActionDone;
import geniusweb.inform.Agreements;
import geniusweb.inform.Finished;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.YourTurn;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;

/*
 * A simple implementation of a SAOP party that can handle only bilateral
 * negotiations (1 other party).
 */

public class MyAgent extends DefaultParty {

    // Minimum utility value of a bid that the agent offers or accepts.
    private final double acceptableValue = 0.7;
    private final Random random = new Random();
    protected ProfileInterface profileint;
    // Last received bid from the opponent
    private Bid lastReceivedBid = null;
    private PartyId me;
    private Progress progress;
    private Profile profile;
    private AllPartialBidsList bidspace;


    public MyAgent() {
    }

    public MyAgent(Reporter reporter) {
        // Reporter is used for debugging
        super(reporter);
    }

    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                Settings settings = (Settings) info;
                this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());
                this.me = settings.getID();
                this.progress = settings.getProgress();
                try {
                    profile = profileint.getProfile();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                bidspace = new AllPartialBidsList(profile.getDomain());
            } else if (info instanceof ActionDone) {
                Action otheract = ((ActionDone) info).getAction();
                if (otheract instanceof Offer) {
                    lastReceivedBid = ((Offer) otheract).getBid();
                }
            } else if (info instanceof YourTurn) {
                myTurn();
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "Final outcome:" + info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
        if (progress instanceof ProgressRounds) {
            progress = ((ProgressRounds) progress).advance();
        }
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SAOP", "Learn")), Collections.singleton(Profile.class));
    }

    @Override
    public String getDescription() {
        return "MyAgent accepts the bids having utility value > " + acceptableValue + ". Offers random bids having utility value >" + acceptableValue + ".";
    }

    /*
     * The function is called when it's our turn so that we can take an action.
     */
    private void myTurn() throws IOException {
        getReporter().log(Level.INFO, "<MyAgent>: It's my turn!");
        Action action = null;
        if (isAcceptable(lastReceivedBid)) {
            // Action of Accepting
            action = new Accept(me, lastReceivedBid);
            getReporter().log(Level.INFO, "<MyAgent>: I am accepting the offer.");
        } else {
            action = makeAnOffer();
        }
        getConnection().send(action);
    }

    private boolean isAcceptable(Bid bid) {
        /* When last received bid is sent to this function, if it is
        * the first round then there is no bid (null) offered by opponent.
        */
        if (bid == null)
            return false;
        // Returns true if utility value of the bid is greater than acceptable value
        return ((UtilitySpace) profile).getUtility(bid).doubleValue() > acceptableValue;
    }


    private Offer makeAnOffer() throws IOException {

        Bid bid = null;
        /* Tries at most 100 times to generate random bids until one of them is acceptable.
        * If there is no, then offers the last generated bid.
        */
        for (int attempt = 0; attempt < 100 && !isAcceptable(bid); attempt++) {
            long i = random.nextInt(bidspace.size().intValue());
            bid = bidspace.get(BigInteger.valueOf(i));
        }
        getReporter().log(Level.INFO, "<MyAgent>: I am offering bid: " + bid);
        // Returns an offering action with the bid found
        return new Offer(me, bid);
    }

}
