package references;

public class Reference<T> {  // previously 'Holder'
    
    public T v;  // the value this reference points to

    public Reference(T value) { this.v = value; }

}
