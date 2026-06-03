import java.util.Scanner;

public class Client
{
    String acct;
    String fname;
    String lname;
    double balance;

    Client next;

    public Client(String a, String f, String l, double b)
    {
        acct = a;
        fname = f;
        lname = l;
        balance = b;

        next = null;
    }

    public Client(Scanner scan)
    {
        System.out.println("Welcome to the Bank");
        System.out.println("*******************");
        System.out.println("Enter your first name:");

        String fname = scan.nextLine();
    }

    @Override
    public String toString()
    {
        return acct + "," + fname + "," + lname + "," + balance;
    }
}
    
    
