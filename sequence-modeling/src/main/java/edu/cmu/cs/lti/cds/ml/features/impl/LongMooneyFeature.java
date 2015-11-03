package edu.cmu.cs.lti.cds.ml.features.impl;

import edu.cmu.cs.lti.cds.ml.features.PairwiseFeature;
import edu.cmu.cs.lti.script.annotators.learn.train.KarlMooneyScriptCounter;
import edu.cmu.cs.lti.script.model.ContextElement;
import org.mapdb.Fun;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: zhengzhongliu
 * Date: 11/30/14
 * Time: 5:36 PM
 */
public class LongMooneyFeature extends PairwiseFeature {
    @Override
    public Map<String, Double> getFeature(ContextElement elementLeft, ContextElement elementRight, int skip) {
        Map<String, Double> features = new HashMap<String, Double>();

        if (skip > 7) {
            return features;
        }

        Fun.Tuple2<Fun.Tuple4<String, Integer, Integer, Integer>, Fun.Tuple4<String, Integer, Integer, Integer>> subsitutedForm = KarlMooneyScriptCounter.
                firstBasedSubstitution(elementLeft.getMention(), elementRight.getMention());
        int[] arg1s = getLast3IntFromTuple(subsitutedForm.a);
        int[] arg2s = getLast3IntFromTuple(subsitutedForm.b);

        String featureName = "m_arg_long" + "_" + asArgumentStr(arg1s) + "_" + asArgumentStr(arg2s);
        features.put(featureName, 1.0);
        return features;
    }

    @Override
    public boolean isLexicalized() {
        return true;
    }

    private int[] getLast3IntFromTuple(Fun.Tuple4<String, Integer, Integer, Integer> t) {
        int[] a = new int[3];
        a[0] = t.b;
        a[1] = t.c;
        a[2] = t.d;
        return a;
    }

    private String asArgumentStr(int[] args) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (int arg : args) {
            sb.append(sep).append(arg);
            sep = "_";
        }
        return sb.toString();
    }
}