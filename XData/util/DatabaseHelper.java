package util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseHelper {
	
	private static Logger logger = Logger.getLogger(DatabaseHelper.class.getName());
	
	
	private static float calculate_score(Connection conn, int assignmentId,String course_id, int questionId, String userId, float marks)
	{
		float ans=0;
		String select_Stmt="select * from xdata_student_queries where course_id=? and assignment_id=? and question_id=? and rollnum=?";
		try(PreparedStatement ps = conn.prepareStatement(select_Stmt)){
			ps.setString(1, course_id);
			ps.setInt(2, assignmentId);
			ps.setInt(3,questionId);
			ps.setString(4, userId);
			try(ResultSet rs = ps.executeQuery()){
			
				if(rs.next()){
					//float auto_grade=rs.getFloat("xdata_score");
					float manual_grade=rs.getFloat("manual_score");
					if(rs.getObject("manual_score") != null && ! rs.wasNull())
						 ans=manual_grade;
					else{
						 ans=marks;
					}
				}
			}
			
		}catch (SQLException e) {
			 logger.log(Level.SEVERE,"Error in DatabaseHelper.java : InsertIntoScores : \n" + e.getMessage(),e);
		}
		
		return ans;
		
	}
	private static float calculate_scaledMarks(Connection conn, int assignmentId,String course_id, int questionId, String userId,float marks)
	{
		float ans=0;
		boolean isScaled=true;
		float scaling_factor=0;
		float max_mark=0;
		String select_scale=" select * from xdata_qinfo where course_id=? and assignment_id=? and question_id=? ";
		try(PreparedStatement ps = conn.prepareStatement(select_scale)){
			ps.setString(1, course_id);
			ps.setInt(2, assignmentId);
			ps.setInt(3,questionId);
			try(ResultSet rs = ps.executeQuery()){
			
				if(rs.next()){
					if(rs.getObject("scale") != null && ! rs.wasNull())
					{
						scaling_factor=rs.getFloat("scale");
					}
					else
					{
						isScaled=false;
					}
					
				}
			}
			
		}catch (SQLException e) {
			 logger.log(Level.SEVERE,"Error in DatabaseHelper.java : InsertIntoScores : \n" + e.getMessage(),e);
		}
		
		String select_max_marks="select max(marks) as mx_mark from xdata_instructor_query where course_id=? and assignment_id=? and question_id=? ";
		try(PreparedStatement ps = conn.prepareStatement(select_max_marks)){
			ps.setString(1, course_id);
			ps.setInt(2, assignmentId);
			ps.setInt(3,questionId);
			try(ResultSet rs = ps.executeQuery()){
			
				if(rs.next()){
					if(rs.getObject("mx_mark") != null && ! rs.wasNull())
					{
						max_mark=rs.getFloat("mx_mark");
					}
					else
					{
						isScaled=false;
					}
					
				}
			}
			
		}catch (SQLException e) {
			 logger.log(Level.SEVERE,"Error in DatabaseHelper.java : InsertIntoScores : \n" + e.getMessage(),e);
		}
		
		if(isScaled==false || max_mark==0)
			return marks;
		ans=marks * (scaling_factor/max_mark);
			return ans;
		
	}
	public static void InsertIntoScores(Connection conn, int assignmentId, int questionId, int queryId, String course_id, int maxMarks, String userId, String info, Float marks, Float raw_marks) throws SQLException{
		//String insertquery="INSERT INTO score VALUES (?,?,?,?,?,?,?,?,?)";
		 
		try{
			
			float newmarks= calculate_score(conn,assignmentId,course_id,questionId, userId,marks);
			float scaled_marks= calculate_scaledMarks(conn,assignmentId,course_id,questionId, userId,newmarks);
			String updateScoreQuery = "update xdata_student_queries set score = ?,markinfo=?,max_marks=?,raw_score=?, xdata_score=?, scaled_score=? where assignment_id=? and question_id=? and rollnum=?";
			try(PreparedStatement ps = conn.prepareStatement(updateScoreQuery)){
			ps.setFloat(1, newmarks);
			ps.setString(2, info);
			ps.setInt(3, maxMarks);
			ps.setFloat(4, raw_marks);
			ps.setFloat(5,marks);
			ps.setFloat(6,scaled_marks);
			ps.setFloat(7, assignmentId);
			ps.setInt(8, questionId);
			ps.setString(9, userId);
			
			ps.executeUpdate();
			}
			
		}catch (SQLException e) {
			 logger.log(Level.SEVERE,"Error in DatabaseHelper.java : InsertIntoScores : \n" + e.getMessage(),e);
			//String update="update score set result = ?, markinfo = ? where assignment_id=? and question_id=? and rollnum=? and course_id=?;";
			 String update="update xdata_student_queries set score = ?, markinfo = ? where assignment_id=? and question_id=? and rollnum=? and course_id=?;";			 
			try(PreparedStatement smt=conn.prepareStatement(update)){
				smt.setFloat(1, marks);
				smt.setString(2, info);
				smt.setInt(3, assignmentId);
				
				smt.setInt(4, questionId);
				smt.setString(5, userId);	
				smt.setString(6,course_id);
				smt.executeUpdate();
				smt.close();
			}
		}
	}
}
