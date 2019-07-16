package client;

public class Message {
	private String type;
	private Object content;
	private String name;

	public Message(String name, String type, Object content) {
		this.name = name;
		this.type = type;
		this.content = content;
	}

	public Message(String name, Object content) {
		this.name = name;
		this.type = "String";
		this.content = content;
	}

	public Message(Object content) {
		this.name = "LOCAL";
		this.type = "String";
		this.content = content;
	}

	public Object get() {
		if (type == "String") {
			return content.toString();
		} else {
			return content;
		}
	}

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

}
