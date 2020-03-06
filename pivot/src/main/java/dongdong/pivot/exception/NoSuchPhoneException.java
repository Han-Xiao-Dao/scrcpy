package dongdong.pivot.exception;

public class NoSuchPhoneException extends RuntimeException {

    private final String phone;

    public NoSuchPhoneException(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

}
