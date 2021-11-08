package inf226.inchat;

public final class UserName {
    private final String Uname;

    public UserName(String username){
        validate(username); //todo handle error
        this.Uname = username;
    }

    public String getUserName(){
        return Uname;
    }

    //todo fix restrictions
    private boolean validate(String userName){
        assert(userName.length() < 1000);
        return false;
    }
}


