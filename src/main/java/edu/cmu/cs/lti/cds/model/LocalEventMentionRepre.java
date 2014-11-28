package edu.cmu.cs.lti.cds.model;

import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.script.type.EventMentionArgumentLink;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.utils.TokenAlignmentHelper;
import edu.cmu.cs.lti.utils.Utils;
import gnu.trove.map.TIntIntMap;

/**
 * Although this is not so different from MooneyEventRepre in the form, we need
 * to differentiate because this would take arbitrary thing for argments, while
 * int Mooney representation we restrict a set of variables
 * <p/>
 * Created with IntelliJ IDEA.
 * User: zhengzhongliu
 * Date: 11/3/14
 * Time: 3:44 PM
 */
public class LocalEventMentionRepre {
    private final String mentionHead;

    //Note, this could be null! Means no argument at this position
    private final LocalArgumentRepre[] args;

    //TODO: change mention head to an integer
    public LocalEventMentionRepre(String mentionHead, LocalArgumentRepre arg0, LocalArgumentRepre arg1, LocalArgumentRepre arg2) {
        this.mentionHead = mentionHead;
        this.args = new LocalArgumentRepre[3];
        this.args[0] = arg0;
        this.args[1] = arg1;
        this.args[2] = arg2;
    }

    public LocalEventMentionRepre(String mentionHead, LocalArgumentRepre... args) {
        this.mentionHead = mentionHead;
        this.args = args;
    }

    public void rewrite(TIntIntMap entityIdRewriteMap) {
        for (LocalArgumentRepre arg : args) {
            arg.setRewritedId(entityIdRewriteMap.get(arg.getEntityId()));
        }
    }

    public static LocalEventMentionRepre fromEventMention(EventMention mention, TokenAlignmentHelper align) {
        LocalArgumentRepre[] args = new LocalArgumentRepre[3];
        for (EventMentionArgumentLink aLink : UimaConvenience.convertFSListToList(mention.getArguments(), EventMentionArgumentLink.class)) {
            String argumentRole = aLink.getArgumentRole();
            if (KmTargetConstants.targetArguments.containsKey(argumentRole)) {
                int slotId = KmTargetConstants.targetArguments.get(argumentRole) - KmTargetConstants.anchorArg0Marker;
                int entityId = Utils.entityIdToInteger(aLink.getArgument().getReferingEntity().getId());
                LocalArgumentRepre arg = new LocalArgumentRepre(entityId, aLink.getArgument().getHead().getLemma());
                args[slotId] = arg;
            }
        }
        return new LocalEventMentionRepre(align.getLowercaseWordLemma(mention.getHeadWord()), args);
    }

    public static LocalEventMentionRepre fromMooneyMention(MooneyEventRepre mention) {
        LocalArgumentRepre[] args = new LocalArgumentRepre[3];
        for (int slotId = 0; slotId < mention.getAllArguments().length; slotId++) {
            int rewriteArgumentId = mention.getAllArguments()[slotId];
            LocalArgumentRepre arg = new LocalArgumentRepre(-1, "", rewriteArgumentId, false);
            arg.setRewritedId(rewriteArgumentId);
        }
        return new LocalEventMentionRepre(mention.getPredicate(), args);
    }


    public String getMentionHead() {
        return mentionHead;
    }

    public LocalArgumentRepre getArg(int i) {
        return args[i];
    }

    public LocalArgumentRepre[] getArgs() {
        return args;
    }

    public int getNumArgs() {
        return args.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mentionHead).append(" : ");
        for (int i = 0; i < args.length; i++) {
            sb.append("\t");
            LocalArgumentRepre arg = args[i];
            if (arg != null) {
                sb.append("arg").append(i).append(" ").append(arg.toString());
            } else {
                sb.append("arg").append(i).append(" null");
            }

        }

        return sb.toString();
    }
}
