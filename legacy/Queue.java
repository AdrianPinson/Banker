public class Queue
{
	Client head;
	Client tail;

	public Queue()
	{
		head = null;
		tail = null;
	}

	//add(enqueue)
	public void add(Client c)
	{
		c.next = null;
		if(head == null)
		{
			head = c;
			tail = c;
		}
		else
		{
			tail.next = c;
			tail = tail.next;
		}

	}

	//serve(dequeue)
	public Client server()
	{
		if(head == null)
		{
			return null;
		}
		Client temp = head;
		head = head.next;

		if(head == null)
		{
			tail = null;
		}
		return temp;
	}

	public boolean peek()
	{
		if(head == null)
		{
			return false;
		}
		return true;
	}
}

