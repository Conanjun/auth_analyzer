package com.protect7.authanalyzer.gui.entity;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import com.protect7.authanalyzer.entities.Session;
import com.protect7.authanalyzer.entities.Token;
import com.protect7.authanalyzer.util.GenericHelper;

public class StatusPanel extends JPanel{
	
	private final JLabel headerLabel = new JLabel("<html><p><strong>Header(s) to Replace</strong></html>");
	private final JLabel headerToReplaceValue = new JLabel("");
	private final JLabel headerRemoveLabel = new JLabel("<html><p><strong>Header(s) to Remove</strong></html>");
	private final JLabel headerToRemoveValue = new JLabel("");
	private final JLabel amountOfFilteredRequestsLabel = new JLabel("");
	private final String SESSION_STARTED_TEXT = "<html><span style='color:green; font-weight: bold'>&#x26AB;</span> Session Running</html>";
	private final String SESSION_PAUSED_TEXT = "<html><span style='color:orange; font-weight: bold'>&#x26AB;</span> Session Paused</html>";
	private boolean running = true;
	private final HashMap<String, JLabel> tokenLabelMap = new HashMap<String, JLabel>();
	private final HashMap<String, JButton> refreshButtonMap = new HashMap<String, JButton>();
	private final HashMap<String, JButton> eraseButtonMap = new HashMap<String, JButton>();
	private int amountOfFilteredRequests = 0;
	private final ImageIcon loaderImageIcon = new ImageIcon(this.getClass().getClassLoader().getResource("loader.gif"));
	private final ImageIcon refreshIcon = new ImageIcon(this.getClass().getClassLoader().getResource("refresh.png"));
	private final ImageIcon eraseIcon = new ImageIcon(this.getClass().getClassLoader().getResource("erase.png"));
	
	private static final long serialVersionUID = -4518448060103739997L;

	public void init(Session session) {
		removeAll();
		amountOfFilteredRequests = 0;
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(10, 0, 0, 0);
	
		add(headerLabel, c);
		
		c.gridx = 1;
		c.anchor = GridBagConstraints.NORTH;
		amountOfFilteredRequestsLabel.setText("");
		add(amountOfFilteredRequestsLabel, c);
		
		c.gridwidth = 2;
		c.gridx = 2;
		c.anchor = GridBagConstraints.WEST;
		JButton onOffSwitch = new JButton(SESSION_STARTED_TEXT);
		if(!running) {
			onOffSwitch.setText(SESSION_PAUSED_TEXT);
		}
		onOffSwitch.addActionListener(e -> {
			if(running) {
				onOffSwitch.setText(SESSION_PAUSED_TEXT);
				running = false;
			}
			else {
				onOffSwitch.setText(SESSION_STARTED_TEXT);
				running = true;
			}
		});		
		add(onOffSwitch, c);
		
		c.gridwidth = 4;
		c.gridx = 0;
		c.gridy++;
		c.insets = new Insets(5, 0, 0, 0);
		add(headerToReplaceValue, c);
		if(session.getHeadersToReplace().equals("")) {
			headerToReplaceValue.setText("No Headers specified");
		}
		else {
			headerToReplaceValue.setText(format(session.getHeadersToReplace(), session));
		}
	
		if(session.isRemoveHeaders()) {
			c.gridy++;
			c.insets = new Insets(10, 0, 0, 0);
			add(headerRemoveLabel, c);
			c.insets = new Insets(5, 0, 0, 0);
			c.gridy++;
			add(headerToRemoveValue, c);
			if(session.getHeadersToRemove().equals("")) {
				headerToRemoveValue.setText("No Headers specified");
			}
			else {
				headerToRemoveValue.setText(format(session.getHeadersToRemove(), session));
			}
		}
		
		c.insets = new Insets(10, 0, 0, 0);
		c.gridy++;
		c.fill = GridBagConstraints.HORIZONTAL;
		if(session.getTokens().size() == 0) {
			JLabel dummyLabel = new JLabel("<html><p style='width:490px'>&nbsp;</p></html>");
			c.gridwidth = 2;
			c.gridx = 0;
			add(dummyLabel, c);
		}
		for(Token token : session.getTokens()) {
			c.gridwidth = 2;
			c.gridx = 0;
			c.anchor = GridBagConstraints.WEST;
			JLabel tokenLabel = new JLabel(getTokenText(token));
			tokenLabelMap.put(token.getName(), tokenLabel);
			add(tokenLabel, c);
			if(token.isAutoExtract() || token.isFromToString()) {
				c.gridx = 2;
				c.gridwidth = 1;
				c.fill = GridBagConstraints.NONE;
				JButton renewButton = new JButton(refreshIcon);
				renewButton.setToolTipText("Refresh Value");
				refreshButtonMap.put(token.getName(), renewButton);
				if(token.getRequest() == null) {
					renewButton.setEnabled(false);
				}
				final StatusPanel statusPanel = this;
				renewButton.addActionListener(e -> {
					renewButton.setIcon(loaderImageIcon);
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							token.renewTokenValue(statusPanel, session);
							renewButton.setIcon(refreshIcon);
						}
					}).start();
				});
				add(renewButton, c);
				JButton eraseButton = new JButton(eraseIcon);
				eraseButton.setToolTipText("Erase Value");
				eraseButtonMap.put(token.getName(), eraseButton);
				if(token.getValue() == null) {
					eraseButton.setEnabled(false);
				}
				eraseButton.addActionListener(e -> {
					token.setValue(null);
					updateTokenStatus(token);
				});
				c.insets = new Insets(10, 10, 0, 0);
				c.gridx = 3;
				add(eraseButton, c);
				c.insets = new Insets(10, 0, 0, 0);
			}
			c.gridy++;
		}		
	}
	
	private String getTokenText(Token token) {
		String tokenInfo = "";
		if(token.isAutoExtract()) {
			tokenInfo = "Auto Extract Value";
		}
		if(token.isStaticValue()) {
			tokenInfo = "Static Value";
		}
		if(token.isFromToString()) {
			tokenInfo = "Extract value from[" + token.getGrepFromString().replace("<", "&lt;") + 
					"] to [" + token.getGrepToString().replace("<", "&lt;") + "]";
		}
		if(token.isPromptForInput()) {
			tokenInfo = "Prompt for Input";
		}
		if(token.isRemove()) {
			tokenInfo = "Remove Parameter";
		}
		if(token.isAutoExtract() || token.isFromToString()) {
			if(token.getValue() != null) {
				SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
				String timestap = sdf.format(new Date());
				tokenInfo += " - Last extracted at " + timestap;
			}
		}
		String tokenValue = "<span>Value: <code>null</code></span>";
		if(token.getValue() != null) {
			String value;
			if(token.getValue().length() > 80) {
				value = token.getValue().substring(0, 80) + "...";
			}
			else {
				value = token.getValue();
			}
			if(token.isFromToString() || token.isAutoExtract()) {
				tokenValue = "Value: <code style='color:green'>" + value.replace("<", "&lt;") + "</code>";				
			}
			else {
				tokenValue = "Value: <code style='color:blue'>" + value.replace("<", "&lt;") + "</code>";
			}
		}
		if(token.isRemove()) {
			tokenValue = "Value: <code style='color:red'>[Remove Parameter]</code>";
		}
		return "<html><p style='width:500px'><strong>" + token.getName().replace("<", "&lt;").replace("\n", "<br>") + 
				"</strong> ("+tokenInfo+")</p> <p style='width:500px'>" +  tokenValue.replace("\n", "<br>") + "</p></html>";
	}
	
	private String format(String text, Session session) {
		String htmlString = "<html><p style='width:600px'>"+text.replace("<", "&lt;").replace("\n", "<br>")+"</p></html>";
		return htmlString;
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public void incrementAmountOfFitleredRequests() {
		amountOfFilteredRequests++;
		amountOfFilteredRequestsLabel.setText("Amount of Filtered Requests: " + amountOfFilteredRequests);
		GenericHelper.uiUpdateAnimation(amountOfFilteredRequestsLabel, Color.RED);
	}
	
	public void updateTokenStatus(Token token) {
		JLabel tokenLabel = tokenLabelMap.get(token.getName());
		tokenLabel.setText(getTokenText(token));
		GenericHelper.uiUpdateAnimation(tokenLabel, new Color(0, 153, 0));
		if(token.getValue() != null) {
			if(refreshButtonMap.get(token.getName()) != null) {
				refreshButtonMap.get(token.getName()).setEnabled(true);
			}
			if(eraseButtonMap.get(token.getName()) != null) {
				eraseButtonMap.get(token.getName()).setEnabled(true);
			}
		}
	}
}
