/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package statechum.analysis.Erlang;

import statechum.Pair;

/**
 *
 * @author ramsay
 */
public class OTPGenFSMBehaviour extends OTPBehaviour {

    public OTPGenFSMBehaviour() {
        super();
        name = "gen_fsm";
        patterns.put("handle_event", new Pair<String,Boolean>("event", Boolean.FALSE));
        patterns.put("handle_sync_event", new Pair<String,Boolean>("sync", Boolean.FALSE));
        patterns.put("handle_info", new Pair<String,Boolean>("info", Boolean.FALSE));

    }
}
