package com.searchcode.app.dao;

import com.searchcode.app.config.IDatabaseConfig;
import com.searchcode.app.config.MySQLDatabaseConfig;
import com.searchcode.app.config.Values;
import com.searchcode.app.dto.CodeIndexDocument;
import com.searchcode.app.dto.SourceCodeDTO;
import com.searchcode.app.model.CodeResult;
import com.searchcode.app.model.searchcode.SearchcodeCodeResult;
import com.searchcode.app.service.Singleton;
import com.searchcode.app.util.Helpers;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SourceCode {
    private final Helpers helpers;
    private final IDatabaseConfig dbConfig;

    public SourceCode() {
        this(new MySQLDatabaseConfig(), Singleton.getHelpers());
    }

    public SourceCode(IDatabaseConfig dbConfig, Helpers helpers) {
        this.dbConfig = dbConfig;
        this.helpers = helpers;
    }

    public synchronized int getMaxId() {
        int maxId = 0;

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = this.dbConfig.getConnection();
            stmt = conn.prepareStatement("select id from code order by id desc limit 1;");
            rs = stmt.executeQuery();

            while (rs.next()) {
                maxId = rs.getInt(1);
            }
        }
        catch (SQLException ex) {
            Singleton.getLogger().severe(" caught a " + ex.getClass() + "\n with message: " + ex.getMessage());
        }
        finally {
            this.helpers.closeQuietly(rs);
            this.helpers.closeQuietly(stmt);
            this.helpers.closeQuietly(conn);
        }

        return maxId;
    }

    public synchronized List<CodeResult> getByIds(List<Integer> codeIds) {
        List<CodeResult> codeResultList = new ArrayList<>();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        StringBuffer stringBuffer = new StringBuffer();
        for (Integer codeId: codeIds) {
            stringBuffer.append(codeId).append(",");
        }
        String codeIdsString = StringUtils.substring(stringBuffer.toString(), 0, -1);

        try {
            conn = this.dbConfig.getConnection();
            stmt = conn.prepareStatement("select c.id,c.filename,c.location," +
                    "substring(uncompress(c.content),1,500000) as content," +
                    "c.hash, r.name as reponame, c.simhash, c.linescount," +
                    "lt.type as languagetype," +
                    "s.name as sourcename, r.sourceurl, r.url, c.blank, c.comment, r.username " +
                    "from code c " +
                    "join repo r ON c.repoid = r.id " +
                    "join languagetype lt ON c.languagename = lt.id " +
                    "join source s ON s.id = r.sourceid " +
                    "where c.id in (" +
                    codeIdsString +
                    ") order by field(c.id, " +
                    codeIdsString +
                    ");");

            rs = stmt.executeQuery();

//            while (rs.next()) {
//                codeResultList.add(new SearchcodeSearchResult(
//                        rs.getInt("id"),
//                        rs.getString("filename"),
//                        rs.getString("location"),
//                        rs.getString("content"),
//                        rs.getString("hash"),
//                        rs.getString("reponame"),
//                        rs.getString("simhash"),
//                        rs.getInt("linescount"),
//                        rs.getString("languagetype"),
//                        rs.getString("sourcename"),
//                        rs.getString("sourceurl"),
//                        rs.getString("url"),
//                        rs.getInt("blank"),
//                        rs.getInt("comment"),
//                        rs.getString("username")
//                ));
//            }
        }
        catch (SQLException ex) {
            Singleton.getLogger().severe(" caught a " + ex.getClass() + "\n with message: " + ex.getMessage());
        }
        finally {
            this.helpers.closeQuietly(rs);
            this.helpers.closeQuietly(stmt);
            this.helpers.closeQuietly(conn);
        }

        return codeResultList;
    }

    public synchronized List<SearchcodeCodeResult> getCodeBetween(int start, int end) {

        List<SearchcodeCodeResult> codeResultList = new ArrayList<>(end - start);

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = this.dbConfig.getConnection();
            stmt = conn.prepareStatement("SELECT c.id, c.repoid, c.filetypeid, c.languagename, r.sourceid, " +
                    "UNCOMPRESS(c.content) AS content, " +
                    "c.filename, " +
                    "c.linescount " +
                    "FROM code c " +
                    "JOIN repo r ON r.id = c.repoid " +
                    "WHERE c.id >= ? AND c.id <= ? AND c.deleted = 0 " +
                    "AND c.languagename not in (select id from languagetype where type in ('text', 'Unknown', 'xml', 'xaml', 'css', 'MSBuild scripts'))");
            stmt.setInt(1, start);
            stmt.setInt(2, end);

            rs = stmt.executeQuery();

            while (rs.next()) {
                codeResultList.add(new SearchcodeCodeResult(
                        rs.getInt("id"),
                        rs.getInt("repoid"),
                        rs.getInt("filetypeid"),
                        rs.getInt("languagename"),
                        rs.getInt("sourceid"),
                        rs.getString("content"),
                        rs.getString("filename"),
                        rs.getInt("linescount")
                ));
            }
        }
        catch (SQLException ex) {
            Singleton.getLogger().severe(" caught a " + ex.getClass() + "\n with message: " + ex.getMessage());
        }
        finally {
            this.helpers.closeQuietly(rs);
            this.helpers.closeQuietly(stmt);
            this.helpers.closeQuietly(conn);
        }

        return codeResultList;
    }

    public Optional<SourceCodeDTO> getByCodeIndexDocument(CodeIndexDocument codeIndexDocument) {
        Optional<SourceCodeDTO> result = Optional.empty();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet resultSet = null;

        try {
            conn = this.dbConfig.getConnection();

            String query = "SELECT `id`, `repoid`, `languageid`, `sourceid`, `ownerid`, `licenseid`, `location`, `filename`, UNCOMPRESS(`content`) AS content, `hash`, `simhash`, `linescount`, `data` FROM `sourcecode` WHERE " +
                    //"repoid=? AND location=? AND filename=?";
                    "location=? AND filename=? LIMIT 1;";

            stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, codeIndexDocument.getFileLocation());
            stmt.setString(2, codeIndexDocument.getFileName());

            resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                result = Optional.of(new SourceCodeDTO(
                        resultSet.getInt("id"),
                        resultSet.getInt("repoid"),
                        resultSet.getInt("languageid"),
                        resultSet.getInt("sourceid"),
                        resultSet.getInt("ownerid"),
                        resultSet.getInt("licenseid"),
                        resultSet.getString("location"),
                        resultSet.getString("filename"),
                        resultSet.getString("content"),
                        resultSet.getString("hash"),
                        resultSet.getString("simhash"),
                        resultSet.getInt("linescount"),
                        resultSet.getString("data")
                        ));
            }

        }
        catch (SQLException ex) {
            Singleton.getLogger().severe(" caught a " + ex.getClass() + "\n with message: " + ex.getMessage());
        }
        finally {
            this.helpers.closeQuietly(resultSet);
            this.helpers.closeQuietly(stmt);
            this.helpers.closeQuietly(conn);
        }

        return result;
    }

    public int saveCode(CodeIndexDocument codeIndexDocument) {

        Optional<SourceCodeDTO> existing = this.getByCodeIndexDocument(codeIndexDocument);

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = this.dbConfig.getConnection();
            String query = "INSERT INTO `sourcecode` (`id`, `repoid`, `languageid`, `sourceid`, `ownerid`, `licenseid`, `location`, `filename`, `content`, `hash`, `simhash`, `linescount`, `data`) VALUES " +
                    "(NULL, ?, (SELECT id FROM languagetype WHERE type = ?), ?, ?, ?, ?, ?, COMPRESS(?), ?, ?, ?, ?)";

            if (existing.isPresent()) {
                return existing.get().getId();
            }

            stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, 31337);
            stmt.setString(2, codeIndexDocument.getLanguageName());
            stmt.setInt(3, 31337);
            stmt.setInt(4, 31337);
            stmt.setInt(5, 31337);
            stmt.setString(6, this.getLocation(codeIndexDocument));
            stmt.setString(7, codeIndexDocument.getFileName());
            stmt.setString(8, codeIndexDocument.getContents());
            stmt.setString(9, codeIndexDocument.getHash());
            stmt.setString(10, "simhash");
            stmt.setInt(11, codeIndexDocument.getCodeLines());
            stmt.setString(12, "{}");

            stmt.execute();
            ResultSet tableKeys = stmt.getGeneratedKeys();
            tableKeys.next();
            int autoGeneratedID = tableKeys.getInt(1);
            return autoGeneratedID;
        }
        catch (SQLException ex) {
            Singleton.getLogger().severe(" caught a " + ex.getClass() + "\n with message: " + ex.getMessage());
        }
        finally {
            this.helpers.closeQuietly(stmt);
            this.helpers.closeQuietly(conn);
        }

        return 0;
    }

    public String getLocation(CodeIndexDocument codeIndexDocument) {
        if (codeIndexDocument == null || codeIndexDocument.getDisplayLocation() == null || codeIndexDocument.getFileName() == null) {
            return Values.EMPTYSTRING;
        }

        return codeIndexDocument.getDisplayLocation().substring(0, codeIndexDocument.getDisplayLocation().length() - codeIndexDocument.getFileName().length());
    }
}
