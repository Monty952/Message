package com.example.message.models;

public class ContactItem {
    private long id;
    private String name;
    private String number;
    private String photoUri;

    public ContactItem(long id, String name, String number, String photoUri) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.photoUri = photoUri;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getNumber() { return number; }
    public String getPhotoUri() { return photoUri; }
}
