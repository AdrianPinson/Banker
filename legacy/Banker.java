import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class Banker
  {
    Scanner scan = new Scanner(System.in);

    String filename = "clients.csv";
    LinkedList database = new LinkedList();
    Queue q = new Queue();

    initialize(filename, database); // loads file into linked list

    System.out.println(database); // lets us know how many in file

    // kiosk menu
    // 1. add to queue
    // 2. serve next
    // 3. close bank
    int choice = 0;

    while(choice !=3)
    {
        System.out.println("\n1. Add Client to Queue");
        System.out.println("2. Serve Next Client");
        System.out.println("3. Close Bank");

        choice = scan.nextInt();

        if(choice == 1)
        {
            System.out.println("Account #: ");
            String acct = scan.nextLine();

            System.out.println("First Name: ");
            String fname = scan.nextLine();

            System.out.println("Last Name: ");
            String lname = scan.nextLine();

            Client c = new Client(acct, fname, lname, 0);
            q.add(c);
            System.out.println("Client added to queue.");
        }
        else if (choice == 2)
        {
            if(!q.peek())
            {
                System.out.println("No clients waiting:");
                continue;
            }

            Client serving = q.server();
            Client record = database.find(serving.acct);
            if(record == null || !record.fname.trim().equalsIgnoreCase(serving.fname.trim())
                    || !record.lname.trim().equals(serving.lname.trim()))
            {
                System.out.println("Verification failed. ");
                continue;
            }

            int serveChoice = 0;

            while(serveChoice != 5)
            {
                System.out.println("\n1. Deposit");
                System.out.println("2. Withdraw");
                System.out.println("3. Check Balance");
                System.out.println("4. Close Account");
                System.out.println("5. End Session");

                serveChoice = scan.nextInt();
                if(serveChoice == 1)
                {
                    System.out.print("Deposit amount: ");
                    double amt = scan.nextDouble();
                    record.balance += amt;
                }
                else if(serveChoice == 2)
                {
                    System.out.print("Withdraw amount: ");
                    double amt = scan.nextDouble();

                    if(amt > record.balance)
                        System.out.println("Insufficient funds.");
                    else
                        record.balance -= amt;
                }
                else if(serveChoice == 3)
                {
                    System.out.println("Balance: $ " + record.balance);
                }
                else if(serveChoice == 4)
                {
                    System.out.println("Balance: $: " + record.balance);
                    System.out.println("Are you sure? (yes/no)");

                    scan.nextLine();
                    String confirm = scan.nextLine();

                    if(confirm.equalsIgnoreCase("yes"))
                    {
                        database.delete(record.acct);
                        System.out.println("Account closed.");
                        serveChoice = 5;
                    }
                }
            }
        }
        else if(choice == 3)
        {
            if(q.peek())
            {
                System.out.println("Queue not empty. Cannont close bank.");
                choice = 0;
            }
            else
            {
                database.saveToFile("clients2.csv");
                System.out.println("Bank closed.";
            }
        }
    }

    //Teller menu - call when serving (loop until done)
    // - verify client matched database
    // - if so, get client info (record from database)
    // 1. deposit
    // 2. withdrawal
    // 3. balance
    // 4. close acct
    // 5. end transactions

    scan.close();

}

private static void initialize(String filename, LinkedList database)
{
    try
    {
        FileReader fr = new FileReader(filename);
        BufferedReader br = new BufferedReader(fr);

        String line;
        String splitBy = ",";

        while((line = br.readLine()) != null)
        {
            //split line to array
            String[] data = line.split(splitBy);
            //new client with data
            Client c = new Client(data[0],data[1],data[2],Double.parseDouble(data[3]));
            //add client to linked list
            database.add(c);
        }

  }
  catch(IOException e)
  {
      System.out.println("File Not Found: " + filename);
  }
}
}
        
  

