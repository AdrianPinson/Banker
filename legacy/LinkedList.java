import java.io.FileWriter;
import java.io.IOException;

public class LinkedList
    {

    Client head;
    Client tail;

    public static int count = 0;

    public LinkedList()
    {
        head = null;
        tail = null;
    }

    public void add(Client c)
    {
        if(head == null)
        {
            head = c;
            tail = c;
        }

        count ++;
    }

    @Override
    public String toString()
    {
        return String.format("Database has %d clients \n", count);
    }

    public Client find(String acct)
   {
       Client temp = head;
       while(temp != null)
       {
           if(temp.acct.equals(acct))
           {
             return temp;
           }
           temp = temp.next;
       }
       return null;
   }

  public void delete(String acct)
  {
      if(head == null)
      {
          return;
      }
      if(head.acct.equals(acct))
      {
          head = head.next;
          count--;
          return;
      }

      Client prev = head;
      Client curr = head.next;

      while(curr != null)
     {
         if(curr.acct.equals(acct))
         {
             prev.next = curr.next;
             if(curr == tail)
             {
                 tail = prev;
             }
             count --;
             return;
         }
         prev = curr;
         curr = curr.next;
     }
  }
  public boolean isEmpty()
  {
      return head == null;
  }

  public void saveToFile(String filename)
  {
      try
      {
          FileWriter fw = new FileWriter(filename);
          Client temp = head;

          while(temp != null)
          {
              fw.write(temp.acct + "," + temp.fname + "," + temp.lname + "," + temp.balance + "\n");

              temp = temp.next;
          }
          fw.close();
          System.out.println("Saved to " + filename);

      }
      catch(IOException e)
      {
          System.out.println("Error saving file. ");
      }
  }
}
          
          
        
     
           
        
        
        

