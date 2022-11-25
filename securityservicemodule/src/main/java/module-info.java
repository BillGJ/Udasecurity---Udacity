module com.udacity.catpoint.security{
    requires com.udacity.catpoint.image;
    requires java.desktop;
    requires java.prefs;
    requires miglayout;
    requires com.google.gson;
    requires com.google.common;

    opens com.udacity.catpoint.security.data to com.google.gson;

}