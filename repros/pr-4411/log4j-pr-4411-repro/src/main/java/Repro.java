import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringMapMessage;
import org.bson.Document;

public final class Repro {

    public static void main(final String[] args) {
        final String collectionName = System.getProperty("collection");
        try (MongoClient client = MongoClients.create("mongodb://127.0.0.1:27017")) {
            final MongoCollection<Document> collection =
                    client.getDatabase("log4j").getCollection(collectionName);
            collection.drop();

            final Logger logger = LogManager.getLogger(Repro.class);
            logger.info(new StringMapMessage().with("_id", "fixed-id").with("user", "u1").with("_rev", "r1"));
            logger.info(new StringMapMessage().with("_id", "fixed-id").with("user", "u2").with("_rev", "r2"));
            LogManager.shutdown();

            final List<String> ids = new ArrayList<>();
            final List<String> users = new ArrayList<>();
            final List<String> revs = new ArrayList<>();
            for (final Document document : collection.find()) {
                final Object id = document.get("_id");
                ids.add(id.getClass().getSimpleName() + ":" + id);
                users.add(String.valueOf(document.get("user")));
                revs.add(String.valueOf(document.get("_rev")));
            }
            final long count = ids.size();
            final boolean noUserId = ids.stream().noneMatch(id -> id.endsWith(":fixed-id"));
            System.out.println((count == 2 ? "PASS " : "FAIL ") + "both-events-stored expected=2 observed=" + count
                    + " users=" + users);
            System.out.println((noUserId && count > 0 ? "PASS " : "FAIL ")
                    + "reserved-_id-not-copied expected=ObjectId observed=" + ids);
            System.out.println((revs.contains("r1") ? "PASS " : "FAIL ")
                    + "non-reserved-_rev-kept-control expected=r1 present observed=" + revs);
            collection.drop();
        }
    }
}
