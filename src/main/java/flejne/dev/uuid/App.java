package flejne.dev.uuid;

import java.util.Scanner;

public final class App {

 
    public static void main(String[] args)
    {
        Scanner in = new Scanner(System.in);
        System.out.print("Enter an UUID: ");
        String userInput = in.next();
        System.out.println("Valid size: "+UuidPlus.isSizeValid.test(userInput));
        System.out.println("Valid versionned UUID: "+UuidPlus.isPatternMatching.test(userInput));
        System.out.println("Valid but not versionned UUID: "+UuidPlus.isPatternMatching.test(userInput));
        in.close(); 
    }
}
