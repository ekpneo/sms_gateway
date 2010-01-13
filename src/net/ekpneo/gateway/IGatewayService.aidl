package net.ekpneo.gateway;

interface IGatewayService {
	boolean enable();
	boolean disable();
	boolean isEnabled();
}