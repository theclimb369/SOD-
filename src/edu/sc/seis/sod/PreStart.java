package edu.sc.seis.sod;

import java.lang.reflect.Method;

public class PreStart {

    public static void main(String[] args) throws Exception {
        if(System.getProperty("java.vm.name").equals("GNU libgcj")) {
            System.err.println("You are running GNU's version of Java, gcj, which doesn't have all the features SOD requires.  Instead, use Sun's Java from http://java.sun.com.");
            System.exit(-1);
        }
        
        Class realStart = Class.forName(args[0]);
        String[] realArgs = new String[args.length - 1];
        System.arraycopy(args, 1, realArgs, 0, realArgs.length);
        Method main = realStart.getMethod("main", new Class[] {String[].class});
        main.invoke(null, new Object[] {realArgs});
    }
}