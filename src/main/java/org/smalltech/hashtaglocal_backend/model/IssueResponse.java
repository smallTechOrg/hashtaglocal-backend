package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import java.time.LocalDateTime;

public class IssueResponse {

    private Data data;

    public IssueResponse() {
    }

    public IssueResponse(Data data) {
        this.data = data;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    // ---------- nested models below ----------

    public static class Data {
        private Issue issue;
        private ViewerContext viewerContext;

        public Data() {
        }

        public Data(Issue issue, ViewerContext viewerContext) {
            this.issue = issue;
            this.viewerContext = viewerContext;
        }

        public Issue getIssue() {
            return issue;
        }

        public void setIssue(Issue issue) {
            this.issue = issue;
        }

        public ViewerContext getViewerContext() {
            return viewerContext;
        }

        public void setViewerContext(ViewerContext viewerContext) {
            this.viewerContext = viewerContext;
        }
    }

    public static class Issue {
        private User user;
        private Location location;
        private String type;
        private String description;
        private LocalDateTime createdAt;                                                                                                                                                                                           
        private List<Media> mediaUrls;
        private int voteCount;
        private int verifyCount;
        private String status;
        private int rank;

        public Issue() {
        }

        public Issue(User user,
                     Location location,
                     String type,
                     String description,
                     LocalDateTime createdAt,
                     List<Media> mediaUrls,
                     int voteCount,
                     int verifyCount,
                     String status,
                     int rank) {
            this.user = user;
            this.location = location;
            this.type = type;
            this.description = description;
            this.createdAt = createdAt;
            this.mediaUrls = mediaUrls;
            this.voteCount = voteCount;
            this.verifyCount = verifyCount;
            this.status = status;
            this.rank = rank;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public List<Media> getMediaUrls() {
            return mediaUrls;
        }

        public void setMediaUrls(List<Media> mediaUrls) {
            this.mediaUrls = mediaUrls;
        }

        public int getVoteCount() {
            return voteCount;
        }

        public void setVoteCount(int voteCount) {
            this.voteCount = voteCount;
        }

        public int getVerifyCount() {
            return verifyCount;
        }

        public void setVerifyCount(int verifyCount) {
            this.verifyCount = verifyCount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }
    }

    public static class User {
        private String username;
        private String profileUrl;

        public User() {
        }

        public User(String username, String profileUrl) {
            this.username = username;
            this.profileUrl = profileUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getProfileUrl() {
            return profileUrl;
        }

        public void setProfileUrl(String profileUrl) {
            this.profileUrl = profileUrl;
        }
    }

    public static class Location {
        private String lat;
        private String lng;
        private Locality locality;
        private String address;
        private String colloquialName;

        public Location() {
        }

        public Location(String lat, String lng, Locality locality, String address, String colloquialName) {
            this.lat = lat;
            this.lng = lng;
            this.locality = locality;
            this.address = address;
            this.colloquialName = colloquialName;
        }

        public String getLat() {
            return lat;
        }

        public void setLat(String lat) {
            this.lat = lat;
        }

        public String getLng() {
            return lng;
        }

        public void setLng(String lng) {
            this.lng = lng;
        }

        public Locality getLocality() {
            return locality;
        }

        public void setLocality(Locality locality) {
            this.locality = locality;
        }
        
        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getColloquialName() {
            return colloquialName;
        }

        public void setColloquialName(String colloquialName) {
            this.colloquialName = colloquialName;
        }
    }

    public static class Locality {
        private List<String> hashtags;

        public Locality() {
        }

        public Locality(List<String> hashtags) {
            this.hashtags = hashtags;
        }

        public List<String> getHashtags() {
            return hashtags;
        }

        public void setHashtags(List<String> hashtags) {
            this.hashtags = hashtags;
        }
    }

    public static class Media {
        private MediaLocation location;
        private String type;
        private String url;

        public Media() {}

        public Media(MediaLocation location, String type, String url) {
            this.location = location;
            this.type = type;
            this.url = url;
        }

        public MediaLocation getLocation() {
            return location;
        }

        public void setLocation(MediaLocation location) {
            this.location = location;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class MediaLocation {

        private String lat;
        private String lng;

        public MediaLocation() {}

        public MediaLocation(String lat, String lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public String getLat() {
            return lat;
        }

        public void setLat(String lat) {
            this.lat = lat;
        }

        public String getLng() {
            return lng;
        }

        public void setLng(String lng) {
            this.lng = lng;
        }
    }

    public static class ViewerContext {
        private boolean upvote;

        public ViewerContext() {
        }

        public ViewerContext(boolean upvote) {
            this.upvote = upvote;
        }

        public boolean isUpvote() {
            return upvote;
        }

        public void setUpvote(boolean upvote) {
            this.upvote = upvote;
        }
    }
}
