package NBT;

import java.nio.ByteBuffer;

public class TagLongArray extends NbtTag {
	
	private static final byte ID = 0b1100;
	private long[] value;
	
	public TagLongArray(long[] value) {
		this.name = "";
		this.value = value;
	}
	
	public TagLongArray(String name, long[] value) {
		this.name = name;
		this.value = value;
	}
	
	
	@Override
	public long[] getValue() {
		return this.value;
	}
	
	public void setValue(long[] value) {
		this.value = value;
	}
	
	public void set(int index, long value) {
		this.value[index] = value;
	}
	
	public long get(int index) {
		return this.value[index];
	}

	@Override
	public byte getId() {
		return ID;
	}

	@Override
	public int getLength() {
		return 3 + this.name.length() + this.getNBTValueLength();
	}

	@Override
	public String toString() {
		return ((this.name.length() > 0)? this.name + ":" : "") + this.getValueAsString();
	}

	@Override
	public byte[] toNBTFormat() {
		ByteBuffer b = ByteBuffer.allocate(this.getLength());
		b.put(ID).putShort((short) this.name.length()).put(this.name.getBytes()).put(this.getValueAsNBT());
		return b.array();
	}

	@Override
	byte[] getValueAsNBT() {
		ByteBuffer b = ByteBuffer.allocate(this.getNBTValueLength());
		b.putInt(this.value.length);
		for (long i : this.value)
			b.putLong(i);
		return b.array();
	}

	@Override
	int getNBTValueLength() {
		return Integer.BYTES + (Long.BYTES * this.value.length);
	}

	@Override
	String getValueAsString() {
		String result = "[L;";
		for (int i = 0; i < this.value.length; i++) {
			result += this.value[i];
			if (i + 1 != this.value.length) result += ",";
		}
		result += "]";
		return result;
	}

}